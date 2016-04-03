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

package io.undertow.servlet.api;

import java.lang.reflect.Constructor;
import java.util.EventListener;

import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.core.ApplicationListeners;
import io.undertow.servlet.util.ConstructorInstanceFactory;

/**
 * @author Stuart Douglas
 */
public class ListenerInfo {


    private final Class<? extends EventListener> listenerClass;
    private volatile InstanceFactory<? extends EventListener> instanceFactory;

    public ListenerInfo(final Class<? extends EventListener> listenerClass, final InstanceFactory<? extends EventListener> instanceFactory) {
        this.listenerClass = listenerClass;
        this.instanceFactory = instanceFactory;
        if(!ApplicationListeners.isListenerClass(listenerClass)) {
            throw UndertowServletMessages.MESSAGES.listenerMustImplementListenerClass(listenerClass);
        }
    }

    public ListenerInfo(final Class<? extends EventListener> listenerClass) {
        this.listenerClass = listenerClass;

        try {
            final Constructor<EventListener> ctor = (Constructor<EventListener>) listenerClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            this.instanceFactory = new ConstructorInstanceFactory<>(ctor);
        } catch (NoSuchMethodException e) {
            throw UndertowServletMessages.MESSAGES.componentMustHaveDefaultConstructor("Listener", listenerClass);
        }
    }

    public InstanceFactory<? extends EventListener> getInstanceFactory() {
        return instanceFactory;
    }

    public void setInstanceFactory(InstanceFactory<? extends EventListener> instanceFactory) {
        this.instanceFactory = instanceFactory;
    }

    public Class<?> getListenerClass() {
        return listenerClass;
    }

    @Override
    public String toString() {
        return "ListenerInfo{" +
                "listenerClass=" + listenerClass +
                '}';
    }
}
