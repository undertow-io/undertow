package io.undertow.util;

import java.util.concurrent.Executor;

/**
 * @author Stuart Douglas
 */
public class SameThreadExecutor implements Executor {

    public static final Executor INSTANCE = new SameThreadExecutor();

    private SameThreadExecutor() {
    }

    @Override
    public void execute(final Runnable command) {
        command.run();
    }
}
