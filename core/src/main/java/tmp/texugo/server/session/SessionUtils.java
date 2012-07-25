/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tmp.texugo.server.session;

import org.xnio.IoFuture;
import tmp.texugo.TexugoMessages;
import tmp.texugo.server.HttpServerExchange;

/**
 * static methods to help with dealing with sessions
 *
 * @author Stuart Douglas
 */
public class SessionUtils {

    private SessionUtils() {

    }

    public static Session createSession(final HttpServerExchange exchange) {
        SessionManager manager = (SessionManager) exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
        if(manager == null) {
            throw TexugoMessages.MESSAGES.sessionManagerNotFound();
        }
        return manager.createSession(exchange);
    }

    public static IoFuture<Session> createSessionAsync(final HttpServerExchange exchange) {
        SessionManager manager = (SessionManager) exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
        if(manager == null) {
            throw TexugoMessages.MESSAGES.sessionManagerNotFound();
        }
        return manager.createSessionAsync(exchange);
    }
}
