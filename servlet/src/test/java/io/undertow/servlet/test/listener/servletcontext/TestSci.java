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

package io.undertow.servlet.test.listener.servletcontext;

import org.junit.Assert;

import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextAttributeEvent;
import jakarta.servlet.ServletContextAttributeListener;
import jakarta.servlet.ServletException;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * @author Stuart Douglas
 */
public class TestSci implements ServletContainerInitializer {

    public static LinkedBlockingDeque<String> DEQUE = new LinkedBlockingDeque<>();

    @Override
    public void onStartup(Set<Class<?>> c, ServletContext ctx) throws ServletException {
        ctx.addListener(new DynamicListener());
        ctx.setAttribute("testDL", "foo");
        Assert.assertNotNull(ctx.getAttribute(ServletContext.TEMPDIR));
    }

    public static class DynamicListener implements ServletContextAttributeListener {

        @Override
        public void attributeAdded(ServletContextAttributeEvent event) {
            DEQUE.add("dl add " + event.getName());
        }

        @Override
        public void attributeRemoved(ServletContextAttributeEvent event) {
            DEQUE.add("dl remove " + event.getName());
        }

        @Override
        public void attributeReplaced(ServletContextAttributeEvent event) {
            DEQUE.add("dl replace " + event.getName());
        }
    }
}
