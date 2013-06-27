/*
 * JBoss, Home of Professional Open Source
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.servlet.test.listener.request.async.onTimeout;

import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;

public class SimpleRequestListener implements ServletRequestListener {

    private static final ThreadLocal<Boolean> IN_REQUEST = new ThreadLocal<Boolean>();
    private static boolean nestedInvocationOccured;

    @Override
    public void requestInitialized(ServletRequestEvent sre) {
        if (IN_REQUEST.get() != null) {
            nestedInvocationOccured = true;
        }
        IN_REQUEST.set(Boolean.TRUE);
    }

    @Override
    public void requestDestroyed(ServletRequestEvent sre) {
        if (IN_REQUEST.get() == null) {
            nestedInvocationOccured = true;
        }
        IN_REQUEST.remove();
    }

    public static boolean hasNestedInvocationOccured() {
        return nestedInvocationOccured;
    }
}
