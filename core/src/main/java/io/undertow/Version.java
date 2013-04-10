package io.undertow;

import java.util.Properties;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
public class Version {
    private static final String versionString;
    private static final String SERVER_NAME = "Undertow";
    private static final String fullVersionString;

    static {
        String version = "Unknown";
        try {
            Properties props = new Properties();
            props.load(Version.class.getResourceAsStream("version.properties"));
            version = props.getProperty("undertow.version");
        } catch (Exception e) {
            e.printStackTrace();
        }
        versionString = version;
        fullVersionString = SERVER_NAME + " - "+ versionString;
    }

    public static String getVersionString() {
        return versionString;
    }

    public static String getFullVersionString() {
        return fullVersionString;
    }
}
