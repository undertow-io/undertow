package io.undertow.server;

import org.junit.AfterClass;
import org.junit.Test;
import org.xnio.Options;

import io.undertow.Undertow;

public class StopTestCase {

    @AfterClass
    public static void waitServerStopCompletely() {
        // sleep 1 s to prevent BindException (Address already in use) when running the tests
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignore) {}
    }

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
        } catch (RuntimeException ignore) {
        }
        undertow.stop();
    }
}
