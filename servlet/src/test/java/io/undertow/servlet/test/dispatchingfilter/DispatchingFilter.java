/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.test.dispatchingfilter;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import java.io.IOException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Dispatching filter whose doFilter and destroy methods share a lock.
 *
 * @author Flavia Rainone
 */
public class DispatchingFilter implements Filter {

    static ReadWriteLock rwLock = new ReentrantReadWriteLock();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest req,
                         ServletResponse res,
                         FilterChain chain) throws IOException, ServletException {
        rwLock.writeLock().lock();
        try {
            if (req.getDispatcherType() != DispatcherType.FORWARD) {
                // race condition test semaphore: flag to test when the deployment should be stopped
                DispatchingFilterTestCase.readyToStop.setResult(true);
                RequestDispatcher dispatcher = req.getServletContext().getRequestDispatcher("/path/servlet");
                dispatcher.forward(req, res);
            }
            chain.doFilter(req, res);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @Override
    public void destroy() {
        rwLock.writeLock().lock();
        rwLock.writeLock().unlock();
    }
}
