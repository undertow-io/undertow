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

package io.undertow.websockets.jsr.osgi;

import io.undertow.servlet.ServletExtension;
import io.undertow.websockets.jsr.Bootstrap;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * OSGi Activator.  This activator will be called when the bundle is started.
 * Its purpose is to register the ServletExtension to support websockets.
 */
public class Activator implements BundleActivator {

    ServiceRegistration<ServletExtension> registration;

    @Override
    public void start(BundleContext context) throws Exception {
        // Register the service in the OSGi registry.
        registration = context.registerService(ServletExtension.class, new Bootstrap(), null);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        // Now, unregister the service.
        registration.unregister();
    }
}
