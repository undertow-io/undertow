package io.undertow.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests that the headers in the Headers class have the correct order. The headers
 * are assigned an explicit ordering integer to allow for super fast comparisons.
 *
 * @author Stuart Douglas
 */
public class HeaderOrderTestCase {

    @Test
    public void testHeadersOrder() throws Exception {

        final Field orderIntField = HttpString.class.getDeclaredField("orderInt");
        orderIntField.setAccessible(true);

        Field[] fields = Headers.class.getDeclaredFields();
        final List<HttpString> headers = new ArrayList<HttpString>();
        for(final Field field : fields) {
            Object value = field.get(null);
            if(!(value instanceof HttpString)) {
                continue;
            }
            HttpString header = (HttpString) value;
            if((Integer)orderIntField.get(header) != 0) {
                headers.add(header);
            }
        }

        Collections.sort(headers, new Comparator<HttpString>() {
            @Override
            public int compare(final HttpString o1, final HttpString o2) {
                return o1.toString().compareToIgnoreCase(o2.toString());
            }
        });
        int val = 1;
        for(final HttpString header : headers) {
            Assert.assertEquals(val++, orderIntField.get(header));
        }

    }

}
