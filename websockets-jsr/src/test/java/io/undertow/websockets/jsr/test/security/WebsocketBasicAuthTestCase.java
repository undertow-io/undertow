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

package io.undertow.websockets.jsr.test.security;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.CloseReason;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.AuthMethodConfig;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.LoginConfig;
import io.undertow.servlet.api.SecurityConstraint;
import io.undertow.servlet.api.SecurityInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.WebResourceCollection;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.security.constraint.ServletIdentityManager;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.util.FlexBase64;
import io.undertow.websockets.jsr.ServerWebSocketContainer;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import io.undertow.websockets.jsr.test.annotated.ClientConfigurator;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.BASIC;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
public class WebsocketBasicAuthTestCase {
    private static final String REALM_NAME = "Servlet_Realm";

    private static ServerWebSocketContainer deployment;
    private static DeploymentManager deploymentManager;

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler path = new PathHandler();

        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletIdentityManager identityManager = new ServletIdentityManager();
        identityManager.addUser("user1", "password1", "role1");
        identityManager.addUser("charsetUser", "password-ü", "role1");

        LoginConfig loginConfig = new LoginConfig(REALM_NAME);
        Map<String, String> props = new HashMap<>();
        props.put("charset", "ISO_8859_1");
        props.put("user-agent-charsets", "Chrome,UTF-8,OPR,UTF-8");
        loginConfig.addFirstAuthMethod(new AuthMethodConfig("BASIC", props));
        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .setIdentityManager(identityManager)
                .setLoginConfig(loginConfig)
                .addFilter(Servlets.filter("wrapper", WrapperFilter.class))
                .addFilterUrlMapping("wrapper", "/wrapper/*", DispatcherType.REQUEST)
                .addServletContextAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME,
                        new WebSocketDeploymentInfo()
                                .setBuffers(DefaultServer.getBufferPool())
                                .setWorker(DefaultServer.getWorker())
                                .addEndpoint(SecuredEndpoint.class)
                                .addListener(containerReady -> deployment = containerReady)
                );

        builder.addSecurityConstraint(new SecurityConstraint()
                .addWebResourceCollection(new WebResourceCollection()
                        .addUrlPattern("/secured/*"))
                .addRoleAllowed("role1")
                .setEmptyRoleSemantic(SecurityInfo.EmptyRoleSemantic.DENY));

        deploymentManager = container.addDeployment(builder);
        deploymentManager.deploy();
        path.addPrefixPath(builder.getContextPath(), deploymentManager.start());

        DefaultServer.setRootHandler(path);
    }

    @AfterClass
    public static void cleanup() throws ServletException {
        if (deployment != null)
            deployment.close();
        if (deploymentManager != null) {
            deploymentManager.stop();
            deploymentManager.undeploy();
        }
    }

    @Test
    public void testAuthenticatedWebsocket() throws Exception {
        ProgramaticClientEndpoint endpoint = new ProgramaticClientEndpoint();
        ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().configurator(new CustomClientConfigurator()).build();
        ContainerProvider.getWebSocketContainer().connectToServer(endpoint, clientEndpointConfig, new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/servletContext/secured"));
        assertEquals("user1", endpoint.getResponses().poll(15, TimeUnit.SECONDS));
        endpoint.session.close();
        assertTrue(endpoint.closeLatch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void testWrappedRequest() throws Exception {
        ProgramaticClientEndpoint endpoint = new ProgramaticClientEndpoint();
        ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
        ContainerProvider.getWebSocketContainer().connectToServer(endpoint, clientEndpointConfig, new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/servletContext/wrapper"));
        assertEquals("wrapped", endpoint.getResponses().poll(15, TimeUnit.SECONDS));
        endpoint.session.close();
        assertTrue(endpoint.closeLatch.await(10, TimeUnit.SECONDS));
    }

    public static class ProgramaticClientEndpoint extends Endpoint {

        private final LinkedBlockingDeque<String> responses = new LinkedBlockingDeque<>();

        final CountDownLatch closeLatch = new CountDownLatch(1);
        volatile Session session;

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            this.session = session;
            session.addMessageHandler(String.class, (message) -> responses.add(message));
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            closeLatch.countDown();
        }

        public LinkedBlockingDeque<String> getResponses() {
            return responses;
        }
    }

    @ServerEndpoint("/{path}")
    public static class SecuredEndpoint {

        @OnOpen
        public void open(Session session) throws IOException {
            session.getBasicRemote().sendText(session.getUserPrincipal().getName());
            session.close();
        }

    }

    public static class WrapperFilter implements Filter {

        @Override
        public void init(FilterConfig filterConfig) {
        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
            filterChain.doFilter(new ServletRequestWrapper((HttpServletRequest) servletRequest), servletResponse);
        }

        @Override
        public void destroy() {

        }
    }

    private static class ServletRequestWrapper extends HttpServletRequestWrapper {

        ServletRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public Principal getUserPrincipal() {
            return () -> "wrapped";
        }
    }

    private static class CustomClientConfigurator extends ClientConfigurator {

        @Override
        public void beforeRequest(Map<String, List<String>> headers) {
            headers.put(AUTHORIZATION.toString(), Collections.singletonList(BASIC + " " + FlexBase64.encodeString("user1:password1".getBytes(), false)));
        }
    }
}
