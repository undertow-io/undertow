package io.undertow.servlet.api;

/**
 * @author Stuart Douglas
 */
public enum ServletStackTraces {

    NONE("none"),
    LOCAL_ONLY("local-only"),
    ALL("all");

    private final String value;

    private ServletStackTraces(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
