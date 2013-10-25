package io.undertow.server.protocol.framed;

/**
 * Frame header data for frames that are received
 *
 * @author Stuart Douglas
 */
public interface FrameHeaderData {

    long getFrameLength();

    AbstractFramedStreamSourceChannel<?, ?, ?> getExistingChannel();
}
