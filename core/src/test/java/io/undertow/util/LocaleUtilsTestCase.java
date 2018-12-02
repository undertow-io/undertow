package io.undertow.util;

import java.util.Locale;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import io.undertow.testutils.category.UnitTest;

@Category(UnitTest.class)
public class LocaleUtilsTestCase {

    @Test
    public void testGetLocaleFromInvalidString() throws Exception {
        Assert.assertEquals(LocaleUtils.getLocaleFromString("-"), new Locale(""));
    }

}
