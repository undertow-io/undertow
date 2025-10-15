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

package io.undertow.servlet;

import jakarta.servlet.Filter;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.Servlet;

import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.ErrorPage;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.LoginConfig;
import io.undertow.servlet.api.SecurityConstraint;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.WebResourceCollection;
import io.undertow.servlet.core.ServletContainerImpl;

import java.util.EventListener;

/**
 * Utility class for building servlet deployments.
 *
 * @author Stuart Douglas
 */
public class Servlets {

    private static volatile ServletContainer container;

    /**
     * Returns the default servlet container. For most embedded use
     * cases this will be sufficient.
     *
     * @return The default servlet container
     */
    public static ServletContainer defaultContainer() {
        if (container != null) {
            return container;
        }
        synchronized (Servlets.class) {
            if (container != null) {
                return container;
            }
            return container = ServletContainer.Factory.newInstance();
        }
    }

    /**
     * Creates a new servlet container.
     *
     * @return A new servlet container
     */
    public static ServletContainer newContainer() {
        return new ServletContainerImpl();
    }

    /**
     * Creates a new servlet deployment info structure
     *
     * @return A new deployment info structure
     */
    public static DeploymentInfo deployment() {
        return new DeploymentInfo();
    }

    /**
     * Creates a new servlet description with the given class. The servlet name is inferred from the simple name of the class.
     *
     * @param servletClass The servlet class
     * @return A new servlet description
     */
    public static ServletInfo servlet(final Class<? extends Servlet> servletClass) {
        return servlet(servletClass.getSimpleName(), servletClass);
    }

    /**
     * Creates a new servlet description with the given name and class
     *
     * @param name         The servlet name
     * @param servletClass The servlet class
     * @return A new servlet description
     */
    public static ServletInfo servlet(final String name, final Class<? extends Servlet> servletClass) {
        return new ServletInfo(name, servletClass);
    }

    /**
     * Creates a new servlet description with the given name and class
     *
     * @param name         The servlet name
     * @param servletClass The servlet class
     * @return A new servlet description
     */
    public static ServletInfo servlet(final String name, final Class<? extends Servlet> servletClass, final InstanceFactory<? extends Servlet> servlet) {
        return new ServletInfo(name, servletClass, servlet);
    }


    /**
     * Creates a new filter description with the given class. The filter name is inferred from the simple name of the class.
     *
     * @param filterClass The filter class
     * @return A new filter description
     */
    public static FilterInfo filter(final Class<? extends Filter> filterClass) {
        return filter(filterClass.getSimpleName(), filterClass);
    }

    /**
     * Creates a new filter description with the given name and class
     *
     * @param name        The filter name
     * @param filterClass The filter class
     * @return A new filter description
     */
    public static FilterInfo filter(final String name, final Class<? extends Filter> filterClass) {
        return new FilterInfo(name, filterClass);
    }

    /**
     * Creates a new filter description with the given name and class
     *
     * @param name        The filter name
     * @param filterClass The filter class
     * @return A new filter description
     */
    public static FilterInfo filter(final String name, final Class<? extends Filter> filterClass, final InstanceFactory<? extends Filter> filter) {
        return new FilterInfo(name, filterClass, filter);
    }

    /**
     * Creates a new multipart config element
     *
     * @param location          the directory location where files will be stored
     * @param maxFileSize       the maximum size allowed for uploaded files
     * @param maxRequestSize    the maximum size allowed for
     *                          multipart/form-data requests
     * @param fileSizeThreshold the size threshold after which files will
     *                          be written to disk
     */
    public static MultipartConfigElement multipartConfig(String location, long maxFileSize, long maxRequestSize, int fileSizeThreshold) {
        return new MultipartConfigElement(location, maxFileSize, maxRequestSize, fileSizeThreshold);
    }

    public static ListenerInfo listener(final Class<? extends EventListener> listenerClass, final InstanceFactory<? extends EventListener> instanceFactory) {
        return new ListenerInfo(listenerClass, instanceFactory);
    }

    public static ListenerInfo listener(final Class<? extends EventListener> listenerClass) {
        return new ListenerInfo(listenerClass);
    }

    public static SecurityConstraint securityConstraint() {
        return new SecurityConstraint();
    }

    public static WebResourceCollection webResourceCollection() {
        return new WebResourceCollection();
    }

    private Servlets() {
    }

    public static LoginConfig loginConfig(String realmName, String loginPage, String errorPage) {
        return new LoginConfig(realmName, loginPage, errorPage);
    }

    public static LoginConfig loginConfig(final String realmName) {
        return new LoginConfig(realmName);
    }

    public static LoginConfig loginConfig(String mechanismName, String realmName, String loginPage, String errorPage) {
        return new LoginConfig(mechanismName, realmName, loginPage, errorPage);
    }

    public static LoginConfig loginConfig(String mechanismName, final String realmName) {
        return new LoginConfig(mechanismName, realmName);
    }

    /**
     * Create an ErrorPage instance for a given exception type
     * @param location      The location to redirect to
     * @param exceptionType The exception type
     * @return              The error page definition
     */
    public static ErrorPage errorPage(String location, Class<? extends Throwable> exceptionType) {
        return new ErrorPage(location, exceptionType);
    }

    /**
     * Create an ErrorPage instance for a given response code
     * @param location      The location to redirect to
     * @param statusCode    The status code
     * @return              The error page definition
     */
    public static ErrorPage errorPage(String location, int statusCode) {
        return new ErrorPage(location, statusCode);
    }

    /**
     * Create an ErrorPage that corresponds to the default error page
     *
     * @param location The error page location
     * @return The error page instance
     */
    public static ErrorPage errorPage(String location) {
        return new ErrorPage(location);
    }
}
