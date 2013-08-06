package io.undertow.server.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.undertow.util.CompletionLatchHandler;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.h2.jdbcx.JdbcConnectionPool;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.sql.DataSource;

/**
 * Tests writing the database (in memory)
 *
 * @author Filipe Ferraz
 */

@RunWith(DefaultServer.class)
public class JDBCLogDatabaseTestCase {

    private static final int NUM_THREADS = 10;
    private static final int NUM_REQUESTS = 12;

    private static final HttpHandler HELLO_HANDLER = new HttpHandler() {
        @Override
        public void handleRequest(final HttpServerExchange exchange) throws Exception {
            exchange.getResponseSender().send("Hello");
        }
    };

    @Test
    public void testSingleLogMessageToDatabase() throws IOException, InterruptedException, SQLException {
        DataSource ds = JdbcConnectionPool.create("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "user", "password");
        Connection conn = null;
        conn = ds.getConnection();
        conn.setAutoCommit(true);
        conn.createStatement().executeUpdate("CREATE TABLE PUBLIC.ACCESS (" +
            " id SERIAL NOT NULL," +
            " remoteHost CHAR(15) NOT NULL," +
            " userName CHAR(15)," +
            " timestamp TIMESTAMP NOT NULL," +
            " virtualHost VARCHAR(64)," +
            " method VARCHAR(8)," +
            " query VARCHAR(255) NOT NULL," +
            " status SMALLINT UNSIGNED NOT NULL," +
            " bytes INT UNSIGNED NOT NULL," +
            " referer VARCHAR(128)," +
            " userAgent VARCHAR(128)," +
            " PRIMARY KEY (id)" +
            " );");

        JDBCLogHandler logHandler = new JDBCLogHandler(HELLO_HANDLER, DefaultServer.getWorker(), "common", JDBCLogDatabaseTestCase.class.getClassLoader());
        logHandler.setDriverName("org.h2.Driver");
        logHandler.setConnectionName("user");
        logHandler.setConnectionPassword("password");
        logHandler.setConnectionURL("jdbc:h2:mem:test");

        CompletionLatchHandler latchHandler;
        DefaultServer.setRootHandler(latchHandler = new CompletionLatchHandler(logHandler));
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            HttpResponse result = client.execute(get);
            latchHandler.await();
            logHandler.awaitWrittenForTest();
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Hello", HttpClientUtils.readResponse(result));
        } finally {
            ResultSet resultDatabase = conn.createStatement().executeQuery("SELECT * FROM PUBLIC.ACCESS;");
            resultDatabase.next();
            Assert.assertEquals("127.0.0.1", resultDatabase.getString(logHandler.getRemoteHostField()));
            Assert.assertEquals("5", resultDatabase.getString(logHandler.getBytesField()));
            Assert.assertEquals("200", resultDatabase.getString(logHandler.getStatusField()));
            conn.createStatement().executeUpdate("DROP TABLE PUBLIC.ACCESS;");
            conn.close();
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testLogLotsOfThreadsToDatabase() throws IOException, InterruptedException, ExecutionException, SQLException {
        DataSource ds = JdbcConnectionPool.create("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "user", "password");
        Connection conn = null;
        conn = ds.getConnection();
        conn.setAutoCommit(true);
        conn.createStatement().executeUpdate("CREATE TABLE PUBLIC.ACCESS (" +
                " id SERIAL NOT NULL," +
                " remoteHost CHAR(15) NOT NULL," +
                " userName CHAR(15)," +
                " timestamp TIMESTAMP NOT NULL," +
                " virtualHost VARCHAR(64)," +
                " method VARCHAR(8)," +
                " query VARCHAR(255) NOT NULL," +
                " status SMALLINT UNSIGNED NOT NULL," +
                " bytes INT UNSIGNED NOT NULL," +
                " referer VARCHAR(128)," +
                " userAgent VARCHAR(128)," +
                " PRIMARY KEY (id)" +
                " );");

        JDBCLogHandler logHandler = new JDBCLogHandler(HELLO_HANDLER, DefaultServer.getWorker(), "combined", JDBCLogDatabaseTestCase.class.getClassLoader());
        logHandler.setDriverName("org.h2.Driver");
        logHandler.setConnectionName("user");
        logHandler.setConnectionPassword("password");
        logHandler.setConnectionURL("jdbc:h2:mem:test");

        CompletionLatchHandler latchHandler;
        DefaultServer.setRootHandler(latchHandler = new CompletionLatchHandler(NUM_REQUESTS * NUM_THREADS, logHandler));

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        try {
            final List<Future<?>> futures = new ArrayList<Future<?>>();
            for (int i = 0; i < NUM_THREADS; ++i) {
                final int threadNo = i;
                futures.add(executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        TestHttpClient client = new TestHttpClient();
                        try {
                            for (int i = 0; i < NUM_REQUESTS; ++i) {
                                HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
                                HttpResponse result = client.execute(get);
                                Assert.assertEquals(200, result.getStatusLine().getStatusCode());
                                final String response = HttpClientUtils.readResponse(result);
                                Assert.assertEquals("Hello", response);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        } finally {
                            client.getConnectionManager().shutdown();
                        }
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdown();
        }
        latchHandler.await();
        logHandler.awaitWrittenForTest();

        ResultSet resultDatabase = conn.createStatement().executeQuery("SELECT COUNT(*) FROM PUBLIC.ACCESS;");

        resultDatabase.next();
        Assert.assertEquals(resultDatabase.getInt(1), NUM_REQUESTS * NUM_THREADS);

        conn.createStatement().executeUpdate("DROP TABLE PUBLIC.ACCESS;");
        conn.close();

    }

}
