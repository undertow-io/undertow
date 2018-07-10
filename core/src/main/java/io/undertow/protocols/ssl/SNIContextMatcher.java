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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;

import io.undertow.UndertowMessages;

public class SNIContextMatcher {

    private final SSLContext defaultContext;
    private final Map<SNIMatcher, SSLContext> wildcards;
    private final Map<SNIMatcher, SSLContext> exacts;

    SNIContextMatcher(SSLContext defaultContext, Map<SNIMatcher, SSLContext> wildcards, Map<SNIMatcher, SSLContext> exacts) {
        this.defaultContext = defaultContext;
        this.wildcards = wildcards;
        this.exacts = exacts;
    }

    public SSLContext getContext(List<SNIServerName> servers) {
        for (Map.Entry<SNIMatcher, SSLContext> entry : exacts.entrySet()) {
            for (SNIServerName server : servers) {
                if (entry.getKey().matches(server)) {
                    return entry.getValue();
                }
            }
        }
        for (Map.Entry<SNIMatcher, SSLContext> entry : wildcards.entrySet()) {
            for (SNIServerName server : servers) {
                if (entry.getKey().matches(server)) {
                    return entry.getValue();
                }
            }
        }
        return defaultContext;
    }

    public SSLContext getDefaultContext() {
        return defaultContext;
    }

    public static class Builder {

        private SSLContext defaultContext;
        private final Map<SNIMatcher, SSLContext> wildcards = new LinkedHashMap<>();
        private final Map<SNIMatcher, SSLContext> exacts = new LinkedHashMap<>();

        public SNIContextMatcher build() {
            if(defaultContext == null) {
                throw UndertowMessages.MESSAGES.defaultContextCannotBeNull();
            }
            return new SNIContextMatcher(defaultContext, wildcards, exacts);
        }

        public SSLContext getDefaultContext() {
            return defaultContext;
        }

        public Builder setDefaultContext(SSLContext defaultContext) {
            this.defaultContext = defaultContext;
            return this;
        }

        public Builder addMatch(String name, SSLContext context) {
            if (name.contains("*")) {
                wildcards.put(SNIHostName.createSNIMatcher(name), context);
            } else {
                exacts.put(SNIHostName.createSNIMatcher(name), context);
            }
            return this;
        }
    }
}
