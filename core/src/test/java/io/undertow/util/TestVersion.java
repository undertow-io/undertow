package io.undertow.util;

import io.undertow.Version;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
public class TestVersion {

    @Test
    public void testVersionSet() {
        Assert.assertNotNull(Version.getVersionString());
        String version = Version.getVersionString();
        System.out.println("version = " + version);
        Assert.assertNotSame("Unknown", version);
    }
}
