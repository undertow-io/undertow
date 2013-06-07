package io.undertow.servlet.test.charset;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;

import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Matej Lazar
 */
@RunWith(DefaultServer.class)
public class ParameterCharacterEncodingTestCase {


    @BeforeClass
    public static void setup() throws ServletException {
        DeploymentUtils.setupServlet(new ServletInfo("servlet", EchoServlet.class)
                .addMapping("/"));
    }

    @Test
    public void testUrlCharacterEncoding() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            String message = "abcčšž";
            String charset = "UTF-8";
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext?charset=" + charset + "&message=" + message);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(message, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Test
    public void testMultipartCharacterEncoding() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            String message = "abcčšž";
            String charset = "UTF-8";

            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext");

            MultipartEntity multipart = new MultipartEntity();
            multipart.addPart("charset", new StringBody(charset, Charset.forName(charset)));
            multipart.addPart("message", new StringBody(message, Charset.forName(charset)));
            post.setEntity(multipart);
            HttpResponse result = client.execute(post);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(message, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testFormDataCharacterEncoding() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            String message = "abcčšž";
            String charset = "UTF-8";

            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext");
            final List<NameValuePair> values = new ArrayList<NameValuePair>();
            values.add(new BasicNameValuePair("charset", charset));
            values.add(new BasicNameValuePair("message", message));
            UrlEncodedFormEntity data = new UrlEncodedFormEntity(values, "UTF-8");
            post.setEntity(data);
            HttpResponse result = client.execute(post);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(message, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
