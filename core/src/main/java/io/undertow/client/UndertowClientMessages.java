package io.undertow.client;

import org.jboss.logging.Messages;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;

import java.io.IOException;
import java.net.URI;

/**
 * starting from 1000
 *
 * @author Emanuel Muckenhuber
 */
@MessageBundle(projectCode = "UT")
public interface UndertowClientMessages {

    UndertowClientMessages MESSAGES = Messages.getBundle(UndertowClientMessages.class);

    // 1000
    @Message(id = 1000, value = "Connection closed")
    String connectionClosed();

    @Message(id = 1001, value = "Request already written")
    IllegalStateException requestAlreadyWritten();

    // 1020
    @Message(id = 1020, value = "Failed to upgrade channel due to response %s (%s)")
    String failedToUpgradeChannel(final int responseCode, String reason);

    // 1030
    @Message(id = 1030, value = "invalid content length %d")
    IllegalArgumentException illegalContentLength(long length);

    @Message(id = 1031, value = "Unknown scheme in URI %s")
    IllegalArgumentException unknownScheme(URI uri);

    @Message(id = 1032, value = "Unknown transfer encoding %s")
    IOException unknownTransferEncoding(String transferEncodingString);

    @Message(id = 1033, value = "Invalid connection state")
    IllegalStateException invalidConnectionState();
}
