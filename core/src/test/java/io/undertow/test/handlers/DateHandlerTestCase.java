package io.undertow.test.handlers;

import java.io.IOException;

import io.undertow.server.handlers.DateHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import io.undertow.util.DateUtils;
import io.undertow.util.TestHttpClient;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class DateHandlerTestCase {

    @BeforeClass
    public static void setup() {
        DefaultServer.setRootHandler(new DateHandler(ResponseCodeHandler.HANDLE_200));
    }

    @Test
    public void testDateHandler() throws IOException, InterruptedException {
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Header date = result.getHeaders("Date")[0];
            final long firstDate = DateUtils.parseDate(date.getValue()).getTime();
            Assert.assertTrue((firstDate + 3000) > System.currentTimeMillis());
            Assert.assertTrue(System.currentTimeMillis() > firstDate);
            HttpClientUtils.readResponse(result);
            Thread.sleep(1500);
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            date = result.getHeaders("Date")[0];
            final long secondDate = DateUtils.parseDate(date.getValue()).getTime();
            Assert.assertTrue((secondDate + 2000) > System.currentTimeMillis());
            Assert.assertTrue(System.currentTimeMillis() > secondDate);
            Assert.assertTrue(secondDate > firstDate);
            HttpClientUtils.readResponse(result);
        } finally {

            client.getConnectionManager().shutdown();
        }
    }

}
