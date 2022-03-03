package io.undertow.util;

import org.xnio.XnioIoThread;
import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

final class Internal {

    public static final class UndertowBlockHoundIntegration implements BlockHoundIntegration {
        @Override
        public void applyTo(BlockHound.Builder builder) {
            builder
                    // Result of a yield when the lock is held. This isn't necessary in jboss-threads 3.1.1.Final
                    // .allowBlockingCallsInside("org.jboss.threads.EnhancedQueueExecutorBase1", "lockTail")

                    // Session creation uses a SecureRandom created using 'new SecureRandom()'. This may be a bug.
                    // If this module is shipped for undertow consumers, it would be better to enumerate the known
                    // undertow call-sites rather than allowing this method explicitly.
                    .allowBlockingCallsInside("sun.security.provider.NativePRNG", "engineNextBytes")

                    // Working as intended
                    .allowBlockingCallsInside("org.xnio.nio.WorkerThread$SynchTask", "run")
                    // Wrap and Unwrap read random data from NativePRNG$NonBlocking which shouldn't block
                    // https://bugs.openjdk.java.net/browse/JDK-8251304
                    .allowBlockingCallsInside("sun.security.ssl.SSLEngineImpl", "wrap")
                    .allowBlockingCallsInside("sun.security.ssl.SSLEngineImpl", "unwrap")
                    .nonBlockingThreadPredicate(next -> next.or(thread -> thread instanceof XnioIoThread));
        }
    }

}
