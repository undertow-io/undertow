/*
 *  @(#)DateUtilsTestCase.java
 *
 *  Copyright 2014 Avantis Mobile Media Group. All rights reserved.
 */
package io.undertow.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * @author Tomasz Knyziak
 */
public class DateUtilsTestCase {

    @Test
    public void testParseFirefoxDate() {

        String firefoxHeader = "Mon, 31 Mar 2014 09:24:49 GMT";
        Date firefoxDate = DateUtils.parseDate(firefoxHeader);

        Assert.assertNotNull(firefoxDate);

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        calendar.set(2014, Calendar.MARCH, 31, 9, 24, 49);
        calendar.set(Calendar.MILLISECOND, 0);

        Assert.assertEquals(calendar.getTime(), firefoxDate);


    }

    @Test
    public void testParseChromeDate() {

        String chromeHeader = "Mon, 31 Mar 2014 09:44:00 GMT";
        Date chromeDate = DateUtils.parseDate(chromeHeader);

        Assert.assertNotNull(chromeDate);

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        calendar.set(2014, Calendar.MARCH, 31, 9, 44, 00);
        calendar.set(Calendar.MILLISECOND, 0);

        Assert.assertEquals(calendar.getTime(), chromeDate);

    }

    @Test
    public void testParseIE9Date() {

        String ie9Header = "Wed, 12 Feb 2014 04:43:29 GMT; length=142951";

        Date ie9Date = DateUtils.parseDate(ie9Header);
        Assert.assertNotNull(ie9Date);

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        calendar.set(2014, Calendar.FEBRUARY, 12, 4, 43, 29);
        calendar.set(Calendar.MILLISECOND, 0);

        Assert.assertEquals(calendar.getTime(), ie9Date);

    }

}
