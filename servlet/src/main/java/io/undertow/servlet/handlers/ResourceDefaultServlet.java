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
package io.undertow.servlet.handlers;

import javax.servlet.DispatcherType;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

/**
 * Default servlet responsible for serving up resources. This is both a handler and a servlet. If no filters
 * match the current path then the resources will be served up asynchronously using the
 * {@link io.undertow.server.HttpHandler#handleRequest(io.undertow.server.HttpServerExchange)} method,
 * otherwise the request is handled as a normal servlet request.
 * <p>
 * By default we only allow a restricted set of extensions.
 * <p>
 * todo: this thing needs a lot more work. In particular:
 * - caching for blocking requests
 * - correct mime type
 * - range/last-modified and other headers to be handled properly
 * - head requests
 * - and probably heaps of other things
 *
 * @author Ondrej Zizka, zizka at seznam.cz
 */
public class ResourceDefaultServlet extends DefaultServlet {

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        this.setResourceManager(getDeployment().getDeploymentInfo().getResourceManager());
    }


    @Override
    protected boolean isAllowed(String path, DispatcherType dispatcherType) {

        if (!path.isEmpty()) {
            if(dispatcherType == DispatcherType.REQUEST) {
                //WFLY-3543 allow the dispatcher to access stuff in web-inf and meta inf
                if (path.startsWith("/META-INF") ||
                        path.startsWith("META-INF") ||
                        path.startsWith("/WEB-INF") ||
                        path.startsWith("WEB-INF")) {
                    return false;
                }
            }
        }

        return super.isAllowed(path, dispatcherType);
    }

}
