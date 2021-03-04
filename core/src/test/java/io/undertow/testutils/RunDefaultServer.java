/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.undertow.testutils;

import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.Statement;

/**
 * This statement wraps another statement and works like an around-type hook on top
 * of the wrapped statement, starting the server before the wrapped statement runs
 * and stopping the server at some point afterwords. Notice the server is started
 * only if it not already up.
 *
 * @author Flavia Rainone
 */
public class RunDefaultServer extends Statement {
    private final Statement next;
    private final RunNotifier runNotifier;
    private boolean stopTheServer;

    /**
     * Constructor.
     *
     * @param next wrapped statement
     * @param runNotifier run notifier for current test run
     */
    RunDefaultServer(final Statement next, final RunNotifier runNotifier) {
        this.next = next;
        this.runNotifier = runNotifier;
    }

    /**
     * If invoked, causes the server to be stopped immediately after the wrapped statement
     * is executed.
     * <br>
     * If not invoked, the server will be programmed to be stopped only after the whole
     * test run is finished, via a {@code RunListener}.
     */
    public void stopTheServerWhenDone() {
        this.stopTheServer = true;
    }

    @Override
    public void evaluate() throws Throwable {
        if (DefaultServer.startServer() && !stopTheServer) {
            runNotifier.addListener(new RunListener() {
                @Override
                public void testRunFinished(final Result result) {
                    // TODO if need arises in the future, add a @StopServerAfterClass class annotation to DefaultServer
                    //  (this annotation will cause the server to shutdown after class runs regardless of whether
                    // there is an @AfterServerStops method in the class
                    DefaultServer.stopServer();
                }
            });
        }
        try {
            next.evaluate();
        } finally {
            if (stopTheServer) {
                DefaultServer.stopServer();
            }
        }
    }
}