package io.undertow.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;

/**
 * @author Oleksandr Radchykov
 */
@RunWith(Parameterized.class)
public class URLUtilsTestCase {

    @Parameterized.Parameters
    public static Object[] spaceCodes() {
        return new Object[] { "%2f", "%2F" };
    }

    @Parameterized.Parameter
    public String spaceCode;

    @Test
    public void testDecodingWithEncodedAndDecodedSlashAndSlashDecodingDisabled() throws Exception {
        String url = "http://localhost:3001/by-path/wild%20card/wild%28west%29/wild" + spaceCode + "wolf";

        final String result = URLUtils.decode(url, Charset.defaultCharset().name(), false, new StringBuilder());
        assertEquals("http://localhost:3001/by-path/wild card/wild(west)/wild" + spaceCode + "wolf", result);
    }

    @Test
    public void testDecodingURLMustNotMutateSpaceSymbolsCaseIfSpaceDecodingDisabled() throws Exception {
        final String url = "http://localhost:3001/wild" + spaceCode + "west";

        final String result = URLUtils.decode(url, Charset.defaultCharset().name(), false, new StringBuilder());
        assertEquals(url, result);
    }

}
