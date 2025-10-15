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
package io.undertow.servlet.test.util;

import io.undertow.servlet.test.constant.GenericServletConstants;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Moulali Shikalwadi
 */
public class ProxyPeerXForwardedHandlerServlet extends HttpServlet {


    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        Map<String, String> resMap = new HashMap<String, String>();
        resMap.put(GenericServletConstants.SERVER_NAME, req.getServerName());
        resMap.put(GenericServletConstants.SERVER_PORT, String.valueOf(req.getServerPort()));
        resMap.put(GenericServletConstants.LOCAL_NAME, req.getLocalName());
        resMap.put(GenericServletConstants.LOCAL_ADDR, req.getLocalAddr());
        resMap.put(GenericServletConstants.LOCAL_PORT, String.valueOf(req.getLocalPort()));
        resMap.put(GenericServletConstants.REMOTE_ADDR, req.getRemoteAddr());
        resMap.put(GenericServletConstants.REMOTE_PORT, String.valueOf(req.getRemotePort()));

        PrintWriter writer = resp.getWriter();
        writer.write(convertWithStream(resMap));
        writer.close();
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }

    public String convertWithStream(Map<?, ?> map) {
        String mapAsString = map.keySet().stream()
                .map(key -> key + "=" + map.get(key))
                .collect(Collectors.joining(", ", "{", "}"));
        return mapAsString;
    }
}
