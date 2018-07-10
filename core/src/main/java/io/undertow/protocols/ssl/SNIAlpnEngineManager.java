/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.protocols.ssl;

import java.util.function.Function;

import javax.net.ssl.SSLEngine;

import io.undertow.protocols.alpn.ALPNEngineManager;

public class SNIAlpnEngineManager implements ALPNEngineManager {
    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public boolean registerEngine(SSLEngine engine, Function<SSLEngine, SSLEngine> selectedFunction) {
        if(!(engine instanceof SNISSLEngine)) {
            return false;
        }
        SNISSLEngine snisslEngine = (SNISSLEngine) engine;
        snisslEngine.setSelectionCallback(selectedFunction);
        return true;
    }
}
