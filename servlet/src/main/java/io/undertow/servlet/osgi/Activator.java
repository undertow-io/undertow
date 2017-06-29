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

package io.undertow.servlet.osgi;

import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.core.ServletExtensionHolder;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

/**
 * OSGi Activator.
 * The activator is called when the bundle is started.
 * It tracks ServletExtension services registered in the OSGi registry
 * and will update the {@link ServletExtensionHolder#getServletExtensions()}
 * list accordingly.
 */
public class Activator implements BundleActivator, ServiceTrackerCustomizer<ServletExtension, ServletExtension> {

    ServiceTracker<ServletExtension, ServletExtension> tracker;

    @Override
    public void start(BundleContext context) throws Exception {
        tracker = new ServiceTracker<>(context, ServletExtension.class, this);
        tracker.open();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        tracker.close();
    }

    @Override
    public ServletExtension addingService(ServiceReference<ServletExtension> reference) {
        return null;
    }

    @Override
    public void modifiedService(ServiceReference<ServletExtension> reference, ServletExtension service) {
        ServletExtensionHolder.getServletExtensions().add(service);
    }

    @Override
    public void removedService(ServiceReference<ServletExtension> reference, ServletExtension service) {
        ServletExtensionHolder.getServletExtensions().remove(service);
    }
}
