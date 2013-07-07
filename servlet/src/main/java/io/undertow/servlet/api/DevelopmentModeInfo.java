package io.undertow.servlet.api;

/**
 * @author Stuart Douglas
 */
public class DevelopmentModeInfo {

    private final boolean displayErrorDetails;

    public DevelopmentModeInfo(boolean displayErrorDetails) {
        this.displayErrorDetails = displayErrorDetails;
    }

    public boolean isDisplayErrorDetails() {
        return displayErrorDetails;
    }
}
