package io.undertow.server.handlers.file;

import io.undertow.predicate.PredicatesHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.builder.PredicatedHandler;
import io.undertow.server.handlers.builder.PredicatedHandlersParser;
import io.undertow.server.handlers.resource.DirectoryUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RunWith(DefaultServer.class)
public class FileHandlerWithPredicateTestCase {
    @Test
    public void testIfHandlerReturnsResourcesWhenItIsInPredicate() throws IOException, URISyntaxException {
        TestHttpClient client = new TestHttpClient();
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        try {
            List<PredicatedHandler> predicatedHandlers = PredicatedHandlersParser.parse("path-prefix(/subdir)-> { set(attribute=%U,value=${remaining}); resource(location='"+ rootPath +"',allow-listing=true) }", FileHandlerWithPredicateTestCase.class.getClassLoader());
            PredicatesHandler predicatesHandler = new PredicatesHandler(new ResponseCodeHandler(200));
            for (PredicatedHandler handler : predicatedHandlers) {
                predicatesHandler.addPredicatedHandler(handler);
            }
            DefaultServer.setRootHandler(predicatesHandler);

            //Make sure that we receive a body with the js needed to render the page.
            CheckResponse(client, "/subdir/?js", true, false);

            //Same for css
            CheckResponse(client, "/subdir/?css", false, false);

            //Make sure that we receive a body with the js needed to render the page.
            CheckResponse(client, "/?js", true, true);

            //Same for css
            CheckResponse(client, "/?css", false, true);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private void CheckResponse(TestHttpClient client, String path, Boolean isJs, Boolean emptyResponseExpected) throws IOException {
        //Make sure that we receive a body with the js needed to render the page.
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + path);
        HttpEntity result = client.execute(get).getEntity();

        if (emptyResponseExpected){
            Assert.assertEquals("The response is not empty", "", EntityUtils.toString(result));
        } else {
            if (isJs){
                Assert.assertEquals("The returned js code is different or empty", DirectoryUtils.Blobs.FILE_JS, EntityUtils.toString(result));
            } else {
                Assert.assertEquals("The returned css code is different or empty", DirectoryUtils.Blobs.FILE_CSS, EntityUtils.toString(result));
            }
        }
    }
}
