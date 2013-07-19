package io.undertow.servlet.api;

/**
 * @author Stuart Douglas
 */
public class DevelopmentModeInfo {

    private final boolean displayErrorDetails;
    private final SessionPersistenceManager sessionPersistenceManager;

    public DevelopmentModeInfo(boolean displayErrorDetails, final SessionPersistenceManager sessionPersistenceManager) {
        this.displayErrorDetails = displayErrorDetails;
        this.sessionPersistenceManager = sessionPersistenceManager;
    }

    public boolean isDisplayErrorDetails() {
        return displayErrorDetails;
    }

    public SessionPersistenceManager getSessionPersistenceManager() {
        return sessionPersistenceManager;
    }
}
