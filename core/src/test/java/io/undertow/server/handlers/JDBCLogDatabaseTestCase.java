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

package io.undertow.server.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.CompletionLatchHandler;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.h2.jdbcx.JdbcConnectionPool;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

    private JdbcConnectionPool ds;


    @Before
    public void setup() throws SQLException {
        ds = JdbcConnectionPool.create("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "user", "password");
        Connection conn = null;
        Statement statement = null;
        try {
            conn = ds.getConnection();
            conn.setAutoCommit(true);
            statement = conn.createStatement();
            statement.executeUpdate("CREATE TABLE PUBLIC.ACCESS (" +
                    " id SERIAL NOT NULL," +
                    " remoteHost CHAR(15) NOT NULL," +
                    " userName CHAR(15)," +
                    " timestamp TIMESTAMP NOT NULL," +
                    " virtualHost VARCHAR(64)," +
                    " method VARCHAR(8)," +
                    " query VARCHAR(255) NOT NULL," +
                    " status SMALLINT NOT NULL," +
                    " bytes INT NOT NULL," +
                    " referer VARCHAR(128)," +
                    " userAgent VARCHAR(128)," +
                    " PRIMARY KEY (id)" +
                    " );");
        } finally {
            if (statement != null) {
                statement.close();
            }
            if (conn != null) {
                conn.close();
            }

        }

    }

    @After
    public void teardown() throws SQLException {

        Connection conn = null;
        Statement statement = null;
        try {
            conn = ds.getConnection();
            conn.setAutoCommit(true);
            statement = conn.createStatement();
            statement.executeUpdate("DROP TABLE PUBLIC.ACCESS;");
        } finally {
            if (statement != null) {
                statement.close();
            }
            if (conn != null) {
                conn.close();
            }

        }
        ds.dispose();
        ds = null;
    }

    @Test
    public void testSingleLogMessageToDatabase() throws IOException, InterruptedException, SQLException {


        JDBCLogHandler logHandler = new JDBCLogHandler(HELLO_HANDLER, DefaultServer.getWorker(), "common", ds);

        CompletionLatchHandler latchHandler;
        DefaultServer.setRootHandler(latchHandler = new CompletionLatchHandler(logHandler));
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            HttpResponse result = client.execute(get);
            latchHandler.await();
            logHandler.awaitWrittenForTest();
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Hello", HttpClientUtils.readResponse(result));
        } finally {
            Connection conn = null;
            Statement statement = null;
            try {
                conn = ds.getConnection();
                statement = conn.createStatement();
                ResultSet resultDatabase = statement.executeQuery("SELECT * FROM PUBLIC.ACCESS;");
                resultDatabase.next();
                // for some reason H2 database version 2 is filling in extra blanks in the remote host field. So, even though
                // Undertow sets "127.0.0.1", h2 database is witing "127.0.0.1      " (length 15), so, just trim it
                Assert.assertEquals(DefaultServer.getDefaultServerAddress().getAddress().getHostAddress(), resultDatabase.getString(logHandler.getRemoteHostField()).trim());
                Assert.assertEquals("5", resultDatabase.getString(logHandler.getBytesField()));
                Assert.assertEquals("200", resultDatabase.getString(logHandler.getStatusField()));
                client.getConnectionManager().shutdown();
            } finally {
                if (statement != null) {
                    statement.close();
                }
                if (conn != null) {
                    conn.close();
                }
            }
        }
    }

    @Test
    public void testLogLotsOfThreadsToDatabase() throws IOException, InterruptedException, ExecutionException, SQLException {

        JDBCLogHandler logHandler = new JDBCLogHandler(HELLO_HANDLER, DefaultServer.getWorker(), "combined", ds);

        CompletionLatchHandler latchHandler;
        DefaultServer.setRootHandler(latchHandler = new CompletionLatchHandler(NUM_REQUESTS * NUM_THREADS, logHandler));

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        try {
            final List<Future<?>> futures = new ArrayList<>();
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
                                Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
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

        Connection conn = null;
        Statement statement = null;
        try {
            conn = ds.getConnection();
            statement = conn.createStatement();

            ResultSet resultDatabase = conn.createStatement().executeQuery("SELECT COUNT(*) FROM PUBLIC.ACCESS;");

            resultDatabase.next();
            Assert.assertEquals(resultDatabase.getInt(1), NUM_REQUESTS * NUM_THREADS);

        } finally {
            if (statement != null) {
                statement.close();
            }
            if (conn != null) {
                conn.close();
            }
        }
    }

}
