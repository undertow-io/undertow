package io.undertow.attribute;

import io.undertow.UndertowMessages;

/**
 * An exception that is thrown when an attribute is read only
 *
 * @author Stuart Douglas
 */
public class ReadOnlyAttributeException extends Exception {

    public ReadOnlyAttributeException() {
    }

    public ReadOnlyAttributeException(final String attributeName, final String newValue) {
        super(UndertowMessages.MESSAGES.couldNotSetAttribute(attributeName, newValue));
    }

}
