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

package io.undertow.test.osgi;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.linkBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.CoreOptions.when;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;

import javax.servlet.Servlet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.io.StreamUtils;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.util.PathUtils;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.wiring.BundleWiring;

/**
 * Simple smoke test for Undertow on OSGi. Starts Undertow, adds a servlet and invokes the servlet
 * URl.
 *
 * @author Harald Wellmann
 *
 */
@RunWith(PaxExam.class)
public class DemoServletOsgiTest {

    private static boolean consoleEnabled = Boolean.valueOf(System.getProperty("equinox.console",
        "false"));
    private static String httpPortNumber = System.getProperty("test.http.port", "8080");

    @Configuration
    public Option[] config() {
        return options(
            when(consoleEnabled).useOptions(systemProperty("osgi.console").value("6666")),

            systemProperty("logback.configurationFile").value(
                "file:" + PathUtils.getBaseDir() + "/src/test/resources/logback.xml"),

            linkBundle("slf4j.api"), linkBundle("ch.qos.logback.core"),
            linkBundle("ch.qos.logback.classic"),

            linkBundle("io.undertow.core"),
            linkBundle("io.undertow.servlet"),
            linkBundle("io.undertow.websockets-jsr"),
            linkBundle("org.jboss.xnio.api"),
            linkBundle("org.jboss.xnio.nio"),
            linkBundle("org.jboss.logging.jboss-logging"),
            linkBundle("org.jboss.spec.javax.annotation.jboss-annotations-api_1.2_spec"),
            linkBundle("org.jboss.spec.javax.websocket.jboss-websocket-api_1.0_spec"),
            linkBundle("org.jboss.spec.javax.servlet.jboss-servlet-api_3.1_spec"),
            linkBundle("org.apache.felix.scr"),

            junitBundles());
    }

    @Test
    public void runServlet() throws Exception {

        // get bundle classloader
        ClassLoader cl = FrameworkUtil.getBundle(getClass()).adapt(BundleWiring.class)
            .getClassLoader();

        // load servlet class via bundle classloader
        @SuppressWarnings("unchecked")
        Class<? extends Servlet> servletClass = (Class<? extends Servlet>) cl
            .loadClass(DemoServlet.class.getName());

        DeploymentInfo servletBuilder = Servlets.deployment().setClassLoader(cl)
            .setContextPath("/myapp").setDeploymentName("test.war")
            .addServlets(Servlets.servlet("DemoServlet", servletClass).addMapping("/*"));

        DeploymentManager manager = Servlets.defaultContainer().addDeployment(servletBuilder);
        manager.deploy();
        PathHandler path = Handlers.path(Handlers.redirect("/myapp")).addPrefixPath("/myapp",
            manager.start());

        Undertow server = Undertow.builder()
            .addHttpListener(Integer.valueOf(httpPortNumber), "localhost").setHandler(path).build();
        server.start();

        URL url = new URL(String.format("http://localhost:%s/myapp", httpPortNumber));
        InputStream is = url.openStream();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        StreamUtils.copyStream(is, os, true);
        assertThat(os.toString(), is("Hello world!\r\n"));
    }
}
