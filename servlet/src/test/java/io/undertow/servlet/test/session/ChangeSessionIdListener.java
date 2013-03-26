package io.undertow.servlet.test.session;

import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionIdListener;

/**
 * @author Stuart Douglas
 */
public class ChangeSessionIdListener implements HttpSessionIdListener {

    public static volatile String oldId;
    public static volatile String newId;

    @Override
    public void sessionIdChanged(final HttpSessionEvent event, final String oldSessionId) {
        this.oldId = oldSessionId;
        this.newId = event.getSession().getId();
    }
}
