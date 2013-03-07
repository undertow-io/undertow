package io.undertow.client;

import org.jboss.logging.Messages;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;

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

}
