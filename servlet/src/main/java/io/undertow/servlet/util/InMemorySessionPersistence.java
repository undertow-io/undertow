package io.undertow.servlet.util;

import io.undertow.servlet.UndertowServletLogger;
import io.undertow.servlet.api.SessionPersistenceManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session persistence implementation that simply stores session information in memory.
 *
 * @author Stuart Douglas
 */
public class InMemorySessionPersistence implements SessionPersistenceManager {

    private static final Map<String, byte[]> data = new ConcurrentHashMap<String, byte[]>();

    @Override
    public void persistSessions(String deploymentName, Map<String, Map<String, Object>> sessionData) {
        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final ObjectOutputStream objectOutputStream = new ObjectOutputStream(out);
            objectOutputStream.writeObject(sessionData);
            objectOutputStream.close();
            data.put(deploymentName, out.toByteArray());
        } catch (Exception e) {
            UndertowServletLogger.ROOT_LOGGER.failedToPersistSessions(e);
        }

    }

    @Override
    public Map<String, Map<String, Object>> loadSessionAttributes(String deploymentName, final ClassLoader classLoader) {
        try {
            byte[] data = this.data.remove(deploymentName);
            if (data != null) {
                final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data));
                return (Map<String, Map<String, Object>>) in.readObject();
            }
        } catch (Exception e) {
            UndertowServletLogger.ROOT_LOGGER.failedtoLoadPersistentSessions(e);
        }
        return null;
    }

    @Override
    public void clear(String deploymentName) {
    }
}
