package io.undertow.util;

import java.util.Locale;

import org.junit.Assert;
import org.junit.Test;

public class LocaleUtilsTestCase {

    @Test
    public void testGetLocaleFromInvalidString() throws Exception {
        Assert.assertEquals(LocaleUtils.getLocaleFromString("-"), new Locale(""));
    }

}
