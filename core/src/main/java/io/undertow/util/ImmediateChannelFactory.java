package io.undertow.util;

import java.nio.channels.Channel;

import org.xnio.channels.ChannelFactory;

/**
 * @author Stuart Douglas
 */
public class ImmediateChannelFactory<T extends Channel> implements ChannelFactory<T> {

    private final T value;

    public ImmediateChannelFactory(final T value) {
        this.value = value;
    }

    @Override
    public T create() {
        return value;
    }
}
