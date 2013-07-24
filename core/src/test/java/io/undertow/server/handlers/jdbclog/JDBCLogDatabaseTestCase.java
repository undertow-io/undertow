package io.undertow.server.handlers.jdbclog;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.FileUtils;

import java.io.File;
import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests writing the access log to a file
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class JDBCLogDatabaseTestCase {

    private static final File logDirectory = new File(System.getProperty("java.io.tmpdir") + "/logs");

    private static final int NUM_THREADS = 10;
    private static final int NUM_REQUESTS = 12;

    @Before
    public void before() {
        logDirectory.mkdirs();
    }

    @After
    public void after() {
        FileUtils.deleteRecursive(logDirectory);
    }

    private static final HttpHandler HELLO_HANDLER = new HttpHandler() {
        @Override
        public void handleRequest(final HttpServerExchange exchange) throws Exception {
            exchange.getResponseSender().send("Hello");
        }
    };

    @Test
    public void testSingleLogMessageToDatabase() throws IOException, InterruptedException {
        File directory = logDirectory;
        File logFileName = new File(directory, "server1.log");

        DefaultJDBCLogReceiver logReceiver = new DefaultJDBCLogReceiver(DefaultServer.getWorker(), directory, "server1");
        DefaultServer.setRootHandler(new JDBCLogHandler(HELLO_HANDLER, logReceiver, "Remote address %h request-length %b", JDBCLogDatabaseTestCase.class.getClassLoader()));
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.addHeader("test-header", "single-val");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Hello", HttpClientUtils.readResponse(result));
            logReceiver.awaitWrittenForTest();
            Assert.assertEquals("Remote address 127.0.0.1 request-length 5\n", FileUtils.readFile(logFileName));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


//    @Test
//    public void testLogLotsOfThreads() throws IOException, InterruptedException, ExecutionException {
//        File directory = logDirectory;
//        File logFileName = new File(directory, "server2.log");
//
//        DefaultJDBCLogReceiver logReceiver = new DefaultJDBCLogReceiver(DefaultServer.getWorker(), directory, "server2");
//        DefaultServer.setRootHandler(new JDBCLogHandler(HELLO_HANDLER, logReceiver, "REQ %{i,test-header}", JDBCLogDatabaseTestCase.class.getClassLoader()));
//
//        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
//        try {
//
//            final List<Future<?>> futures = new ArrayList<Future<?>>();
//            for (int i = 0; i < NUM_THREADS; ++i) {
//                final int threadNo = i;
//                futures.add(executor.submit(new Runnable() {
//                    @Override
//                    public void run() {
//                        TestHttpClient client = new TestHttpClient();
//                        try {
//                            for (int i = 0; i < NUM_REQUESTS; ++i) {
//                                HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
//                                get.addHeader("test-header", "thread-" + threadNo + "-request-" + i);
//                                HttpResponse result = client.execute(get);
//                                Assert.assertEquals(200, result.getStatusLine().getStatusCode());
//                                final String response = HttpClientUtils.readResponse(result);
//                                Assert.assertEquals("Hello", response);
//                            }
//                        } catch (IOException e) {
//                            throw new RuntimeException(e);
//                        } finally {
//                            client.getConnectionManager().shutdown();
//                        }
//                    }
//                }));
//            }
//            for (Future<?> future : futures) {
//                future.get();
//            }
//
//        } finally {
//            executor.shutdown();
//        }
//        logReceiver.awaitWrittenForTest();
//        String completeLog = FileUtils.readFile(logFileName);
//        for (int i = 0; i < NUM_THREADS; ++i) {
//            for (int j = 0; j < NUM_REQUESTS; ++j) {
//                Assert.assertTrue(completeLog.contains("REQ thread-" + i + "-request-" + j));
//            }
//        }
//
//    }

}
