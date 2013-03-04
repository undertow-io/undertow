package io.undertow;

import java.util.Properties;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
public class Version {
    private static final String versionString;

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
    }

    public static String getVersionString() {
        return versionString;
    }
}
