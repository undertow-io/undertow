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

package io.undertow.servlet.util;

import io.undertow.servlet.UndertowServletLogger;
import io.undertow.servlet.api.SessionPersistenceManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session persistence implementation that simply stores session information in memory.
 *
 * @author Stuart Douglas
 */
public class InMemorySessionPersistence implements SessionPersistenceManager {

    private static final Map<String, Map<String, SessionEntry>> data = new ConcurrentHashMap<>();

    @Override
    public void persistSessions(String deploymentName, Map<String, PersistentSession> sessionData) {
        try {
            final Map<String, SessionEntry> serializedData = new HashMap<>();
            for (Map.Entry<String, PersistentSession> sessionEntry : sessionData.entrySet()) {
                Map<String, byte[]> data = new HashMap<>();
                for (Map.Entry<String, Object> sessionAttribute : sessionEntry.getValue().getSessionData().entrySet()) {
                    try {
                        final ByteArrayOutputStream out = new ByteArrayOutputStream();
                        final ObjectOutputStream objectOutputStream = new ObjectOutputStream(out);
                        objectOutputStream.writeObject(sessionAttribute.getValue());
                        objectOutputStream.close();
                        data.put(sessionAttribute.getKey(), out.toByteArray());
                    } catch (Exception e) {
                        UndertowServletLogger.ROOT_LOGGER.failedToPersistSessionAttribute(sessionAttribute.getKey(), sessionAttribute.getValue(), sessionEntry.getKey(), e);
                    }
                }
                serializedData.put(sessionEntry.getKey(), new SessionEntry(sessionEntry.getValue().getExpiration(), data));
            }
            data.put(deploymentName, serializedData);
        } catch (Exception e) {
            UndertowServletLogger.ROOT_LOGGER.failedToPersistSessions(e);
        }

    }

    @Override
    public Map<String, PersistentSession> loadSessionAttributes(String deploymentName, final ClassLoader classLoader) {
        try {
            long time = System.currentTimeMillis();
            Map<String, SessionEntry> data = this.data.remove(deploymentName);
            if (data != null) {
                Map<String, PersistentSession> ret = new HashMap<>();
                for (Map.Entry<String, SessionEntry> sessionEntry : data.entrySet()) {
                    if (sessionEntry.getValue().expiry.getTime() > time) {
                        Map<String, Object> session = new HashMap<>();
                        for (Map.Entry<String, byte[]> sessionAttribute : sessionEntry.getValue().data.entrySet()) {
                            final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(sessionAttribute.getValue()));
                            session.put(sessionAttribute.getKey(), in.readObject());
                        }
                        ret.put(sessionEntry.getKey(), new PersistentSession(sessionEntry.getValue().expiry, session));
                    }
                }
                return ret;
            }
        } catch (Exception e) {
            UndertowServletLogger.ROOT_LOGGER.failedtoLoadPersistentSessions(e);
        }
        return null;
    }

    @Override
    public void clear(String deploymentName) {
    }

    static final class SessionEntry {
        private final Date expiry;
        private final Map<String, byte[]> data;

        private SessionEntry(Date expiry, Map<String, byte[]> data) {
            this.expiry = expiry;
            this.data = data;
        }

    }
}
