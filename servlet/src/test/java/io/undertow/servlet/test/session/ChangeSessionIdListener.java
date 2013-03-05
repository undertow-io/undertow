package io.undertow.servlet.test.session;

import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionIdListener;

/**
 * @author Stuart Douglas
 */
public class ChangeSessionIdListener implements HttpSessionIdListener {

    public volatile static String oldId;
    public volatile static String newId;

    @Override
    public void sessionIdChanged(final HttpSessionEvent event, final String oldSessionId) {
        this.oldId = oldSessionId;
        this.newId = event.getSession().getId();
    }
}
