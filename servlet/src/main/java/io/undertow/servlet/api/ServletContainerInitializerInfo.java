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

import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.util.ConstructorInstanceFactory;

import java.lang.reflect.Constructor;
import java.util.Set;

import jakarta.servlet.ServletContainerInitializer;

/**
 * @author Stuart Douglas
 */
public class ServletContainerInitializerInfo {

    private final Class<? extends ServletContainerInitializer> servletContainerInitializerClass;
    private final InstanceFactory<? extends ServletContainerInitializer> instanceFactory;
    private final Set<Class<?>> handlesTypes;

    public ServletContainerInitializerInfo(final Class<? extends ServletContainerInitializer> servletContainerInitializerClass, final InstanceFactory<? extends ServletContainerInitializer> instanceFactory, final Set<Class<?>> handlesTypes) {
        this.servletContainerInitializerClass = servletContainerInitializerClass;
        this.instanceFactory = instanceFactory;
        this.handlesTypes = handlesTypes;
    }

    public ServletContainerInitializerInfo(final Class<? extends ServletContainerInitializer> servletContainerInitializerClass, final Set<Class<?>> handlesTypes) {
        this.servletContainerInitializerClass = servletContainerInitializerClass;
        this.handlesTypes = handlesTypes;

        try {
            final Constructor<ServletContainerInitializer> ctor = (Constructor<ServletContainerInitializer>) servletContainerInitializerClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            this.instanceFactory = new ConstructorInstanceFactory<>(ctor);
        } catch (NoSuchMethodException e) {
            throw UndertowServletMessages.MESSAGES.componentMustHaveDefaultConstructor("ServletContainerInitializer", servletContainerInitializerClass);
        }
    }

    public Class<? extends ServletContainerInitializer> getServletContainerInitializerClass() {
        return servletContainerInitializerClass;
    }

    /**
     * Returns the actual types present in the deployment that are handled by this ServletContainerInitializer.
     *
     * (i.e. not the types in the {@link jakarta.servlet.annotation.HandlesTypes} annotation, but rather actual types
     * the container has discovered that meet the criteria)
     *
     * @return The handled types
     */
    public Set<Class<?>> getHandlesTypes() {
        return handlesTypes;
    }

    public InstanceFactory<? extends ServletContainerInitializer> getInstanceFactory() {
        return instanceFactory;
    }
}
