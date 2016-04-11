package io.undertow.server;

import org.junit.Test;
import org.xnio.Options;

import io.undertow.Undertow;

public class StopTestCase {

    @Test
    public void testStopUndertowNotStarted() {
        Undertow.builder().build().stop();
    }

    @Test
    public void testStopUndertowAfterExceptionDuringStart() {
        // Making the NioXnioWorker constructor throw an exception, resulting in the Undertow.worker field not getting set.
        Undertow undertow = Undertow.builder().setWorkerOption(Options.WORKER_IO_THREADS, -1).build();
        try {
            undertow.start();
        }
        catch (RuntimeException e) {
        }
        undertow.stop();
    }
}
