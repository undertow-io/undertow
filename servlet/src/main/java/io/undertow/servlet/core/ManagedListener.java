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

package io.undertow.servlet.core;

import java.util.EventListener;

import javax.servlet.ServletException;

import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.api.ListenerInfo;

/**
 * @author Stuart Douglas
 */
public class ManagedListener implements Lifecycle {

    private final ListenerInfo listenerInfo;
    private final boolean programatic;

    private volatile boolean started = false;
    private volatile InstanceHandle<? extends EventListener> handle;

    public ManagedListener(final ListenerInfo listenerInfo, final boolean programatic) {
        this.listenerInfo = listenerInfo;
        this.programatic = programatic;
    }

    public synchronized void start() throws ServletException {
        if (!started) {
            try {
                handle = listenerInfo.getInstanceFactory().createInstance();
            } catch (Exception e) {
                throw UndertowServletMessages.MESSAGES.couldNotInstantiateComponent(listenerInfo.getListenerClass().getName(), e);
            }
            started = true;
        }
    }

    public synchronized void stop() {
        started = false;
        if (handle != null) {
            handle.release();
        }
    }

    public ListenerInfo getListenerInfo() {
        return listenerInfo;
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    public EventListener instance() {
        if (!started) {
            throw UndertowServletMessages.MESSAGES.listenerIsNotStarted();
        }
        return handle.getInstance();
    }

    public boolean isProgramatic() {
        return programatic;
    }

    @Override
    public String toString() {
        return "ManagedListener{" +
                "listenerInfo=" + listenerInfo +
                '}';
    }
}
