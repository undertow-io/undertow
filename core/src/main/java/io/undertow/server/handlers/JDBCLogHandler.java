package io.undertow.server.handlers;

import io.undertow.UndertowLogger;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

import java.io.Closeable;
import java.sql.*;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class JDBCLogHandler implements HttpHandler, Runnable, Closeable {

    private final HttpHandler next;
    private final String formatString;
    private final ExchangeCompletionListener exchangeCompletionListener = new JDBCLogCompletionListener();


    //

    private final Executor logWriteExecutor;

    private final Deque<JDBCLogAttribute> pendingMessages;

    //0 = not running
    //1 = queued
    //2 = running
    @SuppressWarnings("unused")
    private volatile int state = 0;

    private static final AtomicIntegerFieldUpdater<JDBCLogHandler> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(JDBCLogHandler.class, "state");

    protected boolean useLongContentLength = false;

    protected String connectionName = null;

    protected String connectionPassword = null;

    protected Driver driver = null;

    private String driverName;
    private String connectionURL;
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
    private String pattern;

    private Connection conn;
    private PreparedStatement ps;

    private long currentTimeMillis;

    public JDBCLogHandler(final HttpHandler next, final  Executor logWriteExecutor, final String formatString, ClassLoader classLoader) {
        this.next = next;
        this.formatString = formatString;

        driverName = null;
        connectionURL = null;
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
        pattern = "common";
        conn = null;
        ps = null;
        currentTimeMillis = new java.util.Date().getTime();

        this.logWriteExecutor = logWriteExecutor;
        this.pendingMessages = new ConcurrentLinkedDeque<JDBCLogAttribute>();
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

        jdbcLogAttribute.remoteHost = resolveAttribute("%h", exchange);
        jdbcLogAttribute.user = resolveAttribute("%u", exchange);
        jdbcLogAttribute.query = resolveAttribute("%q", exchange);

        jdbcLogAttribute.bytes = Long.valueOf(resolveAttribute("%B", exchange));
        if (jdbcLogAttribute.bytes < 0)
            jdbcLogAttribute.bytes = 0;

        jdbcLogAttribute.status = Integer.valueOf(resolveAttribute("%s", exchange));

        if (jdbcLogAttribute.pattern.equals("combined")) {
            jdbcLogAttribute.virtualHost = resolveAttribute("%v", exchange);
            jdbcLogAttribute.method = resolveAttribute("%m", exchange);
            jdbcLogAttribute.referer = resolveAttribute("\"%{i,referer}\"", exchange);
            jdbcLogAttribute.userAgent = resolveAttribute("\"%{i,user-agent}\"", exchange);
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

        List<JDBCLogAttribute> messages = new ArrayList<JDBCLogAttribute>();
        JDBCLogAttribute msg = null;

        //only grab at most 20 messages at a time
        for (int i = 0; i < 20; ++i) {
            msg = pendingMessages.poll();
            if (msg == null) {
                break;
            }
            messages.add(msg);
        }

        if(!messages.isEmpty()) {
            writeMessage(messages);
        }
    }

    private void writeMessage(List<JDBCLogAttribute> messages) {
        try {
            open();
            prepareStatement();
            for (JDBCLogAttribute jdbcLogAttribute : messages) {
                int numberOfTries = 2;
                while (numberOfTries>0) {
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

            stateUpdater.set(this, 0);
            //check to see if there is still more messages
            //if so then run this again
            if (!pendingMessages.isEmpty()) {
                if (stateUpdater.compareAndSet(this, 0, 1)) {
                    logWriteExecutor.execute(this);
                }
            }
        } catch (SQLException e) {
            UndertowLogger.ROOT_LOGGER.errorWritingJDBCLog(e);
        }
    }

    /**
     * For tests only. Blocks the current thread until all messages are written
     * Just does a busy wait.
     *
     * DO NOT USE THIS OUTSIDE OF A TEST
     */
    void awaitWrittenForTest() throws InterruptedException {
        while (!pendingMessages.isEmpty()) {
            Thread.sleep(10);
        }
        while (state != 0) {
            Thread.sleep(10);
        }
        // temporary solution?
        Thread.sleep(1000);
    }

    protected void open() throws SQLException {
        if (conn != null)
            return ;

        if (driver == null) {
            try {
                Class<?> clazz = Class.forName(driverName);
                driver = (Driver) clazz.newInstance();
            } catch (Throwable e) {
                throw new SQLException(e.getMessage());
            }
        }

        Properties props = new Properties();
        props.put("autoReconnect", "true");
        if (connectionName != null)
            props.put("user", connectionName);
        if (connectionPassword != null)
            props.put("password", connectionPassword);
        conn = driver.connect(connectionURL, props);
        conn.setAutoCommit(true);
    }

    private void prepareStatement() throws SQLException {
        ps = conn.prepareStatement
                ("INSERT INTO " + tableName + " ("
                        + remoteHostField + ", " + userField + ", "
                        + timestampField + ", " + queryField + ", "
                        + statusField + ", " + bytesField + ", "
                        + virtualHostField + ", " + methodField + ", "
                        + refererField + ", " + userAgentField
                        + ") VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
    }

    private String resolveAttribute(String attribute, HttpServerExchange exchange) {
        return ExchangeAttributes.parser(JDBCLogHandler.class.getClassLoader()).parse(attribute).readAttribute(exchange);
    }

    @Override
    public void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                ps.close();
                ps = null;
                conn.close();
                conn = null;
            }
        } catch (SQLException e) {
            UndertowLogger.ROOT_LOGGER.error(e);
        }
    }

    private class JDBCLogAttribute {
        private static final String EMPTY = "";
        protected String remoteHost = EMPTY;
        protected String user = EMPTY;
        protected String query = EMPTY;
        protected long bytes = 0;
        protected int status = 0;
        protected String virtualHost = EMPTY;
        protected String method = EMPTY;
        protected String referer = EMPTY;
        protected String userAgent = EMPTY;
        protected String pattern = "common";
        protected Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    }

    public boolean isUseLongContentLength() {
        return useLongContentLength;
    }

    public void setUseLongContentLength(boolean useLongContentLength) {
        this.useLongContentLength = useLongContentLength;
    }

    public String getConnectionName() {
        return connectionName;
    }

    public void setConnectionName(String connectionName) {
        this.connectionName = connectionName;
    }

    public String getConnectionPassword() {
        return connectionPassword;
    }

    public void setConnectionPassword(String connectionPassword) {
        this.connectionPassword = connectionPassword;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public String getConnectionURL() {
        return connectionURL;
    }

    public void setConnectionURL(String connectionURL) {
        this.connectionURL = connectionURL;
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
