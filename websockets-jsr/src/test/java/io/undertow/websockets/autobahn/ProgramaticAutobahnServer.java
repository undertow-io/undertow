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
package io.undertow.websockets.autobahn;

import javax.servlet.DispatcherType;

import io.undertow.Undertow;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.websockets.jsr.JsrWebSocketFilter;
import io.undertow.websockets.jsr.ServerEndpointConfigImpl;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class ProgramaticAutobahnServer implements Runnable {

    private final int port;

    public ProgramaticAutobahnServer(final int port) {
        this.port = port;
    }

    public void run() {

        try {

            final ServletContainer container = ServletContainer.Factory.newInstance();
            DeploymentInfo builder = new DeploymentInfo()
                    .setClassLoader(ProgramaticAutobahnServer.class.getClassLoader())
                    .setContextPath("/")
                    .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                    .setDeploymentName("servletContext.war")
                    .addFilter(new FilterInfo("filter", JsrWebSocketFilter.class))
                    .addFilterUrlMapping("filter", "/*", DispatcherType.REQUEST)

                    .addServletContextAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME,
                            new WebSocketDeploymentInfo()
                                    .setDispatchToWorkerThread(true)
                                    .addEndpoint(new ServerEndpointConfigImpl(ProgramaticAutobahnEndpoint.class, "/"))
                    );

            DeploymentManager manager = container.addDeployment(builder);
            manager.deploy();


            Undertow.builder().addHttpListener(port, "localhost")
                    .setHandler(manager.start())
                    .build();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static void main(String[] args) {
        new ProgramaticAutobahnServer(7777).run();
    }

}
