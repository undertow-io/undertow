/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.spec;

import jakarta.servlet.ServletConnection;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class ServletConnectionImpl implements ServletConnection {

    private final String connectionId;
    private final String protocol;
    private final boolean secure;

    ServletConnectionImpl(final String connectionId, final String protocol, final boolean secure) {
        this.connectionId = connectionId;
        this.protocol = protocol;
        this.secure = secure;
    }

    @Override
    public String getConnectionId() {
        return connectionId;
    }

    @Override
    public String getProtocol() {
        return protocol;
    }

    @Override
    public String getProtocolConnectionId() {
        return "";
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

}
