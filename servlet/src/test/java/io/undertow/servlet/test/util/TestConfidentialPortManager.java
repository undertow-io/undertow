/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
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
package io.undertow.servlet.test.util;

import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.api.ConfidentialPortManager;
import io.undertow.testutils.DefaultServer;

/**
 * Implementation of {@see ConfidentialPortManager} for use within the test suite.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class TestConfidentialPortManager implements ConfidentialPortManager {

    public static final TestConfidentialPortManager INSTANCE = new TestConfidentialPortManager();

    private TestConfidentialPortManager() {
    }

    @Override
    public int getConfidentialPort(HttpServerExchange exchange) {
        return DefaultServer.getHostSSLPort("default");
    }

}
