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
package io.undertow.util;

import io.undertow.testutils.category.UnitTest;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * @author Tomasz Knyziak
 */
@Category(UnitTest.class)
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

    @Test
    @Ignore("This test can fail if the machine pauses/swaps at the wrong time")
    public void testPerformance() {

        String ie9Header = "Wed, 12 Feb 2014 04:43:29 GMT; length=142951";

        long timestamp = System.currentTimeMillis();
        for (int i=0; i < 1000; i++) {
            ie9Header.replaceAll(";.*$", "");
        }
        long ts1 = System.currentTimeMillis() - timestamp;

        timestamp = System.currentTimeMillis();

        for (int i=0; i < 1000; i++) {
            int index = ie9Header.indexOf(';');
            final String trimmedDate = index >=0 ? ie9Header.substring(0, index) : ie9Header;
        }

        long ts2 = System.currentTimeMillis() - timestamp;

        Assert.assertTrue(ts2 < ts1);

    }


}
