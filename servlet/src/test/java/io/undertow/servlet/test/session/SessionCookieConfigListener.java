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

package io.undertow.servlet.test.session;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.SessionTrackingMode;
import java.util.Arrays;
import java.util.HashSet;

/**
 * @author Stuart Douglas
 */
public class SessionCookieConfigListener implements ServletContextListener {
    @Override
    public void contextInitialized(final ServletContextEvent sce) {
        sce.getServletContext().getSessionCookieConfig().setName("MySessionCookie");
        sce.getServletContext().getSessionCookieConfig().setPath("/servletContext/aa/");
        sce.getServletContext().setSessionTrackingModes(new HashSet<>(Arrays.asList(SessionTrackingMode.COOKIE, SessionTrackingMode.URL)));
    }

    @Override
    public void contextDestroyed(final ServletContextEvent sce) {

    }
}
