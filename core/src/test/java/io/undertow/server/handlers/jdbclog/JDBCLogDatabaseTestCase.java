package io.undertow.server.handlers.jdbclog;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

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

        DefaultJDBCLogReceiver logReceiver = new DefaultJDBCLogReceiver(DefaultServer.getWorker());

        logReceiver.setDriverName("org.h2.Driver");
        logReceiver.setConnectionName("user");
        logReceiver.setConnectionPassword("password");
        logReceiver.setConnectionURL("jdbc:h2:mem:test");

        DefaultServer.setRootHandler(new JDBCLogHandler(HELLO_HANDLER, logReceiver, "common", JDBCLogDatabaseTestCase.class.getClassLoader()));
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            HttpResponse result = client.execute(get);
            logReceiver.awaitWrittenForTest();
//            Thread.sleep(3000);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Hello", HttpClientUtils.readResponse(result));
        } finally {
            ResultSet resultDatabase = conn.createStatement().executeQuery("SELECT * FROM PUBLIC.ACCESS;");
            resultDatabase.next();
            Assert.assertEquals("127.0.0.1", resultDatabase.getString(logReceiver.getRemoteHostField()));
            conn.close();
            client.getConnectionManager().shutdown();
        }
    }

}
