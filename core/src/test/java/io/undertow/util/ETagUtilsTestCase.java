package io.undertow.util;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Stuart Douglas
 */
public class ETagUtilsTestCase {

    @Test
    public void testParseHeaderList() {

        Assert.assertArrayEquals(new ETag[] {
                new ETag(false, "1"),
                new ETag(false, "2"),
                new ETag(false, "3")},
                ETagUtils.parseETagList("\"1\",\"2\"   , \"3 ").toArray());

        Assert.assertArrayEquals(new ETag[] {
                new ETag(true, "111"),
                new ETag(false, "222"),
                new ETag(true, "333")},
                ETagUtils.parseETagList("W/\"111\",\"222\"   , W/\"333 ").toArray());

        Assert.assertArrayEquals(new ETag[] {
                new ETag(true, "1,1"),
                new ETag(false, "222"),
                new ETag(true, "3 3")},
                ETagUtils.parseETagList("W/\"1,1\",\"222\"   , W/\"3 3 ").toArray());

    }

}
