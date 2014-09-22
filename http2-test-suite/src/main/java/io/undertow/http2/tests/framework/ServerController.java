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

package io.undertow.http2.tests.framework;

/**
 * Interface that allows the test framework to control the server lifecycle. This can be ignored
 * and the server started manually if desired.
 *
 * Implementations of this class are loaded through the service loader interface.
 *
 * @author Stuart Douglas
 */
public interface ServerController {

    void start(String host, int httpPort, int httpsPort);


    void stop();
}
