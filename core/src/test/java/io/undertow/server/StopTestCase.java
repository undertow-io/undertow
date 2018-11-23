package io.undertow.server;

import org.junit.Test;

import io.undertow.Undertow;
import io.undertow.util.UndertowOptions;

public class StopTestCase {

    @Test
    public void testStopUndertowNotStarted() {
        Undertow.builder().build().stop();
    }

    @Test
    public void testStopUndertowAfterExceptionDuringStart() {
        // Making the NioXnioWorker constructor throw an exception, resulting in the Undertow.worker field not getting set.
        Undertow undertow = Undertow.builder().setWorkerOption(UndertowOptions.WORKER_IO_THREADS, -1).build();
        try {
            undertow.start();
        }
        catch (RuntimeException e) {
        }
        undertow.stop();
    }
}
