/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.server.security;

import static javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag.REQUIRED;
import io.undertow.testutils.DefaultServer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.ldif.LdifEntry;
import org.apache.directory.api.ldap.model.ldif.LdifReader;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.server.core.api.CoreSession;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.partition.Partition;
import org.apache.directory.server.core.factory.DefaultDirectoryServiceFactory;
import org.apache.directory.server.core.factory.DirectoryServiceFactory;
import org.apache.directory.server.core.factory.PartitionFactory;
import org.apache.directory.server.core.kerberos.KeyDerivationInterceptor;
import org.apache.directory.server.kerberos.KerberosConfig;
import org.apache.directory.server.kerberos.kdc.KdcServer;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.directory.server.protocol.shared.transport.Transport;
import org.apache.directory.server.protocol.shared.transport.UdpTransport;

/**
 * Utility class to start up a test KDC backed by a directory server.
 *
 * It is better to start the server once instead of once per test but once running
 * the overhead is minimal.  However a better solution may be to use the {@link Suite}
 * runner but we currently need to use the {@link DefaultServer} runner.
 *
 * TODO - May be able to add some lifecycle methods to DefaultServer to allow
 * for an extension.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class KerberosKDCUtil {

    private static final boolean IS_IBM = System.getProperty("java.vendor").contains("IBM");

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    static final int LDAP_PORT = 11389;
    static final int KDC_PORT = 6088;

    private static final String DIRECTORY_NAME = "Test Service";
    private static boolean initialised;
    private static File workingDir;

    /*
     * LDAP Related
     */
    private static DirectoryService directoryService;
    private static LdapServer ldapServer;

    /*
     * KDC Related
     */
    private static KdcServer kdcServer;



    public static boolean startServer() throws Exception {
        if (initialised) {
            return false;
        }
        startLdapServer();
        startKDC();
        setupEnvironment();

        initialised = true;
        return true;
    }

    private static void startLdapServer() throws Exception {
        createWorkingDir();
        DirectoryServiceFactory dsf = new DefaultDirectoryServiceFactory();
        dsf.init(DIRECTORY_NAME);
        directoryService = dsf.getDirectoryService();
        directoryService.addLast(new KeyDerivationInterceptor()); // Derives the Kerberos keys for new entries.
        directoryService.getChangeLog().setEnabled(false);
        SchemaManager schemaManager = directoryService.getSchemaManager();

        createPartition(dsf, schemaManager, "users", "ou=users,dc=undertow,dc=io");

        CoreSession adminSession = directoryService.getAdminSession();
        Map<String, String> mappings = Collections.singletonMap("hostname", DefaultServer.getDefaultServerAddress().getHostString());
        processLdif(schemaManager, adminSession, "partition.ldif", mappings);
        processLdif(schemaManager, adminSession, "krbtgt.ldif", mappings);
        processLdif(schemaManager, adminSession, "user.ldif", mappings);
        processLdif(schemaManager, adminSession, "server.ldif", mappings);

        ldapServer = new LdapServer();
        ldapServer.setServiceName("DefaultLDAP");
        Transport ldap = new TcpTransport( "0.0.0.0", LDAP_PORT, 3, 5 );
        ldapServer.addTransports(ldap);
        ldapServer.setDirectoryService(directoryService);
        ldapServer.start();
    }

    private static void createPartition(final DirectoryServiceFactory dsf, final SchemaManager schemaManager, final String id,
            final String suffix) throws Exception {
        PartitionFactory pf = dsf.getPartitionFactory();
        Partition p = pf.createPartition(schemaManager, id, suffix, 1000, workingDir);
        pf.addIndex(p, "krb5PrincipalName", 10);
        p.initialize();
        directoryService.addPartition(p);
    }

    private static void processLdif(final SchemaManager schemaManager, final CoreSession adminSession, final String ldifName,
            final Map<String, String> mappings) throws Exception {
        InputStream resourceInput = KerberosKDCUtil.class.getResourceAsStream("/ldif/" + ldifName);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(resourceInput.available());
        int current;
        while ((current = resourceInput.read()) != -1) {
            if (current == '$') {
                // Enter String replacement mode.
                int second = resourceInput.read();
                if (second == '{') {
                    ByteArrayOutputStream substitute = new ByteArrayOutputStream();
                    while ((current = resourceInput.read()) != -1 && current != '}') {
                        substitute.write(current);
                    }
                    if (current == -1) {
                        baos.write(current);
                        baos.write(second);
                        baos.write(substitute.toByteArray()); // Terminator never found.
                    }
                    String toReplace = new String(substitute.toByteArray(), UTF_8);
                    if (mappings.containsKey(toReplace)) {
                        baos.write(mappings.get(toReplace).getBytes());
                    } else {
                        throw new IllegalArgumentException(String.format("No mapping found for '%s'", toReplace));
                    }
                } else {
                    baos.write(current);
                    baos.write(second);
                }
            } else {
                baos.write(current);
            }
        }

        ByteArrayInputStream ldifInput = new ByteArrayInputStream(baos.toByteArray());
        LdifReader ldifReader = new LdifReader(ldifInput);
        for (LdifEntry ldifEntry : ldifReader) {
            adminSession.add(new DefaultEntry(schemaManager, ldifEntry.getEntry()));
        }
        ldifReader.close();
        ldifInput.close();
    }

    private static void startKDC() throws Exception {
        kdcServer = new KdcServer();
        kdcServer.setServiceName("Test KDC");
        kdcServer.setSearchBaseDn("ou=users,dc=undertow,dc=io");
        KerberosConfig config = kdcServer.getConfig();
        config.setServicePrincipal("krbtgt/UNDERTOW.IO@UNDERTOW.IO");
        config.setPrimaryRealm("UNDERTOW.IO");

        config.setPaEncTimestampRequired(false);

        UdpTransport udp = new UdpTransport("0.0.0.0", KDC_PORT);
        kdcServer.addTransports(udp);

        kdcServer.setDirectoryService(directoryService);
        kdcServer.start();
    }

    private static void setupEnvironment() {
        final URL configPath = KerberosKDCUtil.class.getResource("/krb5.conf");
        System.setProperty("java.security.krb5.conf", configPath.getFile());
    }

    private static void createWorkingDir() throws IOException {
        if (workingDir == null) {
            if (workingDir == null) {
                workingDir = new File(".");
                workingDir = new File(workingDir, "target");
                workingDir = new File(workingDir, "apacheds_working").getCanonicalFile();
                if (!workingDir.exists()) {
                    workingDir.mkdirs();
                }
            }
        }
        for (File current : workingDir.listFiles()) {
          current.delete();
        }
    }

    static Subject login(final String userName, final char[] password) throws LoginException {
        Subject theSubject = new Subject();
        CallbackHandler cbh = new UsernamePasswordCBH(userName, password);
        LoginContext lc = new LoginContext("KDC", theSubject, cbh, createJaasConfiguration());
        lc.login();

        return theSubject;
    }

    private static Configuration createJaasConfiguration() {
        return new Configuration() {

            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                if (!"KDC".equals(name)) {
                    throw new IllegalArgumentException("Unexpected name '" + name + "'");
                }

                AppConfigurationEntry[] entries = new AppConfigurationEntry[1];
                Map<String, Object> options = new HashMap<>();
                options.put("debug", "true");
                options.put("refreshKrb5Config", "true");

                if (IS_IBM) {
                    options.put("noAddress", "true");
                    options.put("credsType", "both");
                    entries[0] = new AppConfigurationEntry("com.ibm.security.auth.module.Krb5LoginModule", REQUIRED, options);
                } else {
                    options.put("storeKey", "true");
                    options.put("isInitiator", "true");
                    entries[0] = new AppConfigurationEntry("com.sun.security.auth.module.Krb5LoginModule", REQUIRED, options);
                }

                return entries;
            }

        };
    }

    private static class UsernamePasswordCBH implements CallbackHandler {

        /*
         * Note: We use CallbackHandler implementations like this in test cases as test cases need to run unattended, a true
         * CallbackHandler implementation should interact directly with the current user to prompt for the username and
         * password.
         *
         * i.e. In a client app NEVER prompt for these values in advance and provide them to a CallbackHandler like this.
         */

        private final String username;
        private final char[] password;

        private UsernamePasswordCBH(final String username, final char[] password) {
            this.username = username;
            this.password = password;
        }

        @Override
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (Callback current : callbacks) {
                if (current instanceof NameCallback) {
                    NameCallback ncb = (NameCallback) current;
                    ncb.setName(username);
                } else if (current instanceof PasswordCallback) {
                    PasswordCallback pcb = (PasswordCallback) current;
                    pcb.setPassword(password);
                } else {
                    throw new UnsupportedCallbackException(current);
                }
            }

        }

    }

}
