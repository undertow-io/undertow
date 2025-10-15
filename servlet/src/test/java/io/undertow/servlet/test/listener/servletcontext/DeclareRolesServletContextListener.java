/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
/**
 *
 * @author baranowb
 *
 */
public class DeclareRolesServletContextListener implements ServletContextListener{

    public static final String[] ROLES = new String[] {"dobby","was","here"};
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.err.println(sce.getServletContext());
        sce.getServletContext().declareRoles(ROLES);
    }

}
