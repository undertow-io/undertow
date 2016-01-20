package io.undertow.util;

import org.junit.Assert;
import org.junit.Test;

public class LocaleUtilsTestCase {

    @Test
    public void testGetLocaleFromInvalidString() throws Exception {
        Assert.assertNull(LocaleUtils.getLocaleFromString("-"));
    }

}
