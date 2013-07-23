package io.undertow.servlet.util;

import io.undertow.servlet.UndertowServletLogger;
import io.undertow.servlet.api.SessionPersistenceManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session persistence implementation that simply stores session information in memory.
 *
 * @author Stuart Douglas
 */
public class InMemorySessionPersistence implements SessionPersistenceManager {

    private static final Map<String, Map<String, Map<String, byte[]>>> data = new ConcurrentHashMap<String, Map<String, Map<String, byte[]>>>();

    @Override
    public void persistSessions(String deploymentName, Map<String, Map<String, Object>> sessionData) {
        try {
            final Map<String, Map<String, byte[]>> serializedData = new HashMap<String, Map<String, byte[]>>();
            for (Map.Entry<String, Map<String, Object>> sessionEntry : sessionData.entrySet()) {
                Map<String, byte[]> data = new HashMap<String, byte[]>();
                for (Map.Entry<String, Object> sessionAttribute : sessionEntry.getValue().entrySet()) {
                    try {
                        final ByteArrayOutputStream out = new ByteArrayOutputStream();
                        final ObjectOutputStream objectOutputStream = new ObjectOutputStream(out);
                        objectOutputStream.writeObject(sessionAttribute.getValue());
                        objectOutputStream.close();
                        data.put(sessionAttribute.getKey(), out.toByteArray());
                    } catch (Exception e) {
                        UndertowServletLogger.ROOT_LOGGER.failedToPersistSessionAttribute(sessionAttribute.getKey(), sessionAttribute.getValue(), sessionEntry.getKey());
                    }
                }
                serializedData.put(sessionEntry.getKey(), data);
            }
            data.put(deploymentName, serializedData);
        } catch (Exception e) {
            UndertowServletLogger.ROOT_LOGGER.failedToPersistSessions(e);
        }

    }

    @Override
    public Map<String, Map<String, Object>> loadSessionAttributes(String deploymentName, final ClassLoader classLoader) {
        try {
            Map<String, Map<String, byte[]>> data = this.data.remove(deploymentName);
            if (data != null) {
                Map<String, Map<String, Object>> ret = new HashMap<String, Map<String, Object>>();
                for (Map.Entry<String, Map<String, byte[]>> sessionEntry : data.entrySet()) {
                    Map<String, Object> session = new HashMap<String, Object>();
                    for (Map.Entry<String, byte[]> sessionAttribute : sessionEntry.getValue().entrySet()) {
                        final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(sessionAttribute.getValue()));
                        session.put(sessionAttribute.getKey(), in.readObject());
                    }
                    ret.put(sessionEntry.getKey(), session);
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
}
