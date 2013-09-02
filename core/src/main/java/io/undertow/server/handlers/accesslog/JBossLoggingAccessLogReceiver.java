package io.undertow.server.handlers.accesslog;

import org.jboss.logging.Logger;

/**
 * Access log receiver that logs messages at INFO level.
 *
 * @author Stuart Douglas
 */
public class JBossLoggingAccessLogReceiver implements AccessLogReceiver {

    public static final String DEFAULT_CATEGORY = "io.undertow.accesslog";

    private final Logger logger;

    public JBossLoggingAccessLogReceiver(final String category) {
        this.logger = Logger.getLogger(category);
    }

    public JBossLoggingAccessLogReceiver() {
        this.logger = Logger.getLogger(DEFAULT_CATEGORY);
    }

    @Override
    public void logMessage(String message) {
        logger.info(message);
    }
}
