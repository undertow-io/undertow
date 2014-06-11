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

import io.undertow.UndertowLogger;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class JDBCLogHandler implements HttpHandler, Runnable {

    private final HttpHandler next;
    private final String formatString;
    private final ExchangeCompletionListener exchangeCompletionListener = new JDBCLogCompletionListener();


    private final Executor logWriteExecutor;

    private final Deque<JDBCLogAttribute> pendingMessages;

    //0 = not running
    //1 = queued
    //2 = running
    @SuppressWarnings("unused")
    private volatile int state = 0;

    private static final AtomicIntegerFieldUpdater<JDBCLogHandler> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(JDBCLogHandler.class, "state");

    protected boolean useLongContentLength = false;

    private final DataSource dataSource;

    private String tableName;
    private String remoteHostField;
    private String userField;
    private String timestampField;
    private String virtualHostField;
    private String methodField;
    private String queryField;
    private String statusField;
    private String bytesField;
    private String refererField;
    private String userAgentField;

    public JDBCLogHandler(final HttpHandler next, final Executor logWriteExecutor, final String formatString, DataSource dataSource) {
        this.next = next;
        this.formatString = formatString;
        this.dataSource = dataSource;

        tableName = "access";
        remoteHostField = "remoteHost";
        userField = "userName";
        timestampField = "timestamp";
        virtualHostField = "virtualHost";
        methodField = "method";
        queryField = "query";
        statusField = "status";
        bytesField = "bytes";
        refererField = "referer";
        userAgentField = "userAgent";
        this.logWriteExecutor = logWriteExecutor;
        this.pendingMessages = new ConcurrentLinkedDeque<>();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.addExchangeCompleteListener(exchangeCompletionListener);
        next.handleRequest(exchange);
    }

    private class JDBCLogCompletionListener implements ExchangeCompletionListener {
        @Override
        public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {
            try {
                logMessage(formatString, exchange);
            } finally {
                nextListener.proceed();
            }
        }
    }

    public void logMessage(String pattern, HttpServerExchange exchange) {
        JDBCLogAttribute jdbcLogAttribute = new JDBCLogAttribute();

        if (pattern.equals("combined")) {
            jdbcLogAttribute.pattern = pattern;
        }
        jdbcLogAttribute.remoteHost = ((InetSocketAddress) exchange.getConnection().getPeerAddress()).getAddress().getHostAddress();
        SecurityContext sc = exchange.getSecurityContext();
        if (sc == null || !sc.isAuthenticated()) {
            jdbcLogAttribute.user = null;
        } else {
            jdbcLogAttribute.user = sc.getAuthenticatedAccount().getPrincipal().getName();
        }
        jdbcLogAttribute.query = exchange.getQueryString();

        jdbcLogAttribute.bytes = exchange.getResponseContentLength();
        if (jdbcLogAttribute.bytes < 0)
            jdbcLogAttribute.bytes = 0;

        jdbcLogAttribute.status = exchange.getResponseCode();

        if (jdbcLogAttribute.pattern.equals("combined")) {
            jdbcLogAttribute.virtualHost = exchange.getRequestHeaders().getFirst(Headers.HOST);
            jdbcLogAttribute.method = exchange.getRequestMethod().toString();
            jdbcLogAttribute.referer = exchange.getRequestHeaders().getFirst(Headers.REFERER);
            jdbcLogAttribute.userAgent = exchange.getRequestHeaders().getFirst(Headers.USER_AGENT);
        }

        this.pendingMessages.add(jdbcLogAttribute);
        int state = stateUpdater.get(this);
        if (state == 0) {
            if (stateUpdater.compareAndSet(this, 0, 1)) {
                logWriteExecutor.execute(this);
            }
        }
    }

    /**
     * insert the log record to database
     */
    @Override
    public void run() {
        if (!stateUpdater.compareAndSet(this, 1, 2)) {
            return;
        }

        List<JDBCLogAttribute> messages = new ArrayList<>();
        JDBCLogAttribute msg = null;

        //only grab at most 1000 messages at a time
        for (int i = 0; i < 1000; ++i) {
            msg = pendingMessages.poll();
            if (msg == null) {
                break;
            }
            messages.add(msg);
        }
        try {
            if (!messages.isEmpty()) {
                writeMessage(messages);
            }
        } finally {
            stateUpdater.set(this, 0);
            //check to see if there is still more messages
            //if so then run this again
            if (!pendingMessages.isEmpty()) {
                if (stateUpdater.compareAndSet(this, 0, 1)) {
                    logWriteExecutor.execute(this);
                }
            }
        }
    }

    private void writeMessage(List<JDBCLogAttribute> messages) {
        PreparedStatement ps = null;
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(true);
            ps = prepareStatement(conn);
            for (JDBCLogAttribute jdbcLogAttribute : messages) {
                int numberOfTries = 2;
                while (numberOfTries > 0) {
                    try {
                        ps.clearParameters();
                        ps.setString(1, jdbcLogAttribute.remoteHost);
                        ps.setString(2, jdbcLogAttribute.user);
                        ps.setTimestamp(3, jdbcLogAttribute.timestamp);
                        ps.setString(4, jdbcLogAttribute.query);
                        ps.setInt(5, jdbcLogAttribute.status);
                        if (useLongContentLength) {
                            ps.setLong(6, jdbcLogAttribute.bytes);
                        } else {
                            if (jdbcLogAttribute.bytes > Integer.MAX_VALUE)
                                jdbcLogAttribute.bytes = -1;
                            ps.setInt(6, (int) jdbcLogAttribute.bytes);
                        }
                        ps.setString(7, jdbcLogAttribute.virtualHost);
                        ps.setString(8, jdbcLogAttribute.method);
                        ps.setString(9, jdbcLogAttribute.referer);
                        ps.setString(10, jdbcLogAttribute.userAgent);

                        ps.executeUpdate();
                        numberOfTries = 0;
                    } catch (SQLException e) {
                        UndertowLogger.ROOT_LOGGER.error(e);
                    }
                    numberOfTries--;
                }
            }
            ps.close();
        } catch (SQLException e) {
            UndertowLogger.ROOT_LOGGER.errorWritingJDBCLog(e);
        } finally {
            if (ps != null) {
                try {
                    ps.close();
                } catch (SQLException e) {
                    UndertowLogger.ROOT_LOGGER.debug("Exception closing prepared statement", e);
                }
            }
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    UndertowLogger.ROOT_LOGGER.debug("Exception closing connection", e);
                }
            }
        }
    }

    /**
     * For tests only. Blocks the current thread until all messages are written
     * Just does a busy wait.
     * <p/>
     * DO NOT USE THIS OUTSIDE OF A TEST
     */
    void awaitWrittenForTest() throws InterruptedException {
        while (!pendingMessages.isEmpty()) {
            Thread.sleep(10);
        }
        while (state != 0) {
            Thread.sleep(10);
        }
    }

    private PreparedStatement prepareStatement(Connection conn) throws SQLException {
        return conn.prepareStatement
                ("INSERT INTO " + tableName + " ("
                        + remoteHostField + ", " + userField + ", "
                        + timestampField + ", " + queryField + ", "
                        + statusField + ", " + bytesField + ", "
                        + virtualHostField + ", " + methodField + ", "
                        + refererField + ", " + userAgentField
                        + ") VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
    }

    private class JDBCLogAttribute {
        protected String remoteHost = "";
        protected String user = "";
        protected String query = "";
        protected long bytes = 0;
        protected int status = 0;
        protected String virtualHost = "";
        protected String method = "";
        protected String referer = "";
        protected String userAgent = "";
        protected String pattern = "common";
        protected Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    }

    public boolean isUseLongContentLength() {
        return useLongContentLength;
    }

    public void setUseLongContentLength(boolean useLongContentLength) {
        this.useLongContentLength = useLongContentLength;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getRemoteHostField() {
        return remoteHostField;
    }

    public void setRemoteHostField(String remoteHostField) {
        this.remoteHostField = remoteHostField;
    }

    public String getUserField() {
        return userField;
    }

    public void setUserField(String userField) {
        this.userField = userField;
    }

    public String getTimestampField() {
        return timestampField;
    }

    public void setTimestampField(String timestampField) {
        this.timestampField = timestampField;
    }

    public String getVirtualHostField() {
        return virtualHostField;
    }

    public void setVirtualHostField(String virtualHostField) {
        this.virtualHostField = virtualHostField;
    }

    public String getMethodField() {
        return methodField;
    }

    public void setMethodField(String methodField) {
        this.methodField = methodField;
    }

    public String getQueryField() {
        return queryField;
    }

    public void setQueryField(String queryField) {
        this.queryField = queryField;
    }

    public String getStatusField() {
        return statusField;
    }

    public void setStatusField(String statusField) {
        this.statusField = statusField;
    }

    public String getBytesField() {
        return bytesField;
    }

    public void setBytesField(String bytesField) {
        this.bytesField = bytesField;
    }

    public String getRefererField() {
        return refererField;
    }

    public void setRefererField(String refererField) {
        this.refererField = refererField;
    }

    public String getUserAgentField() {
        return userAgentField;
    }

    public void setUserAgentField(String userAgentField) {
        this.userAgentField = userAgentField;
    }

    @Override
    public String toString() {
        return "JDBCLogHandler{" +
                "formatString='" + formatString + '\'' +
                '}';
    }

}
