/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.test.security.form;

import jakarta.servlet.ServletException;

import java.io.IOException;
import java.net.URI;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.LoginConfig;
import io.undertow.servlet.api.SecurityConstraint;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.ServletSecurityInfo;
import io.undertow.servlet.api.WebResourceCollection;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.security.constraint.ServletIdentityManager;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(DefaultServer.class)
public class FormAuthenticationRootContextRedirectTestCase {

   @BeforeClass
   public static void setup() throws ServletException {
      final PathHandler path = new PathHandler();
      final ServletContainer container = ServletContainer.Factory.newInstance();

      ServletInfo securedIndexRequestDumper = new ServletInfo("SecuredIndexRequestDumperServlet", SaveOriginalPostRequestTestCase.RequestDumper.class)
         .setServletSecurityInfo(new ServletSecurityInfo()
                                    .addRoleAllowed("role1"))
         .addMapping("/index.html");

      ServletInfo loginFormServlet = new ServletInfo("loginPage", FormLoginServlet.class)
         .setServletSecurityInfo(new ServletSecurityInfo()
                                    .addRoleAllowed("group1"))
         .addMapping("/FormLoginServlet");

      ServletIdentityManager identityManager = new ServletIdentityManager();

      identityManager.addUser("user1", "password1", "role1");

      SecurityConstraint securityConstraint = new SecurityConstraint();
      WebResourceCollection webResourceCollection = new WebResourceCollection();
      webResourceCollection.addUrlPattern("/*");
      securityConstraint.addWebResourceCollection(webResourceCollection);
      securityConstraint.addRoleAllowed("role1");

      DeploymentInfo builder = new DeploymentInfo()
         .setClassLoader(SimpleServletTestCase.class.getClassLoader())
         .setContextPath("/servletContext")
         .setClassIntrospecter(TestClassIntrospector.INSTANCE)
         .setDeploymentName("servletContext.war")
         .setIdentityManager(identityManager)
         .addWelcomePage("index.html")
         .setResourceManager(new TestResourceLoader(SaveOriginalPostRequestTestCase.class))
         .addSecurityConstraint(securityConstraint)
         .setLoginConfig(new LoginConfig("FORM", "Test Realm", "/FormLoginServlet", "/error.html"))
         .addServlets(loginFormServlet, securedIndexRequestDumper);

      DeploymentManager manager = container.addDeployment(builder);

      manager.deploy();

      path.addPrefixPath(builder.getContextPath(), manager.start());

      DefaultServer.setRootHandler(path);
   }

   @Test
   public void test2() throws IOException {
      TestHttpClient client = new TestHttpClient();
      HttpClientContext context = HttpClientContext.create();
      String uri = DefaultServer.getDefaultServerURL() + "/servletContext";

      HttpGet request = new HttpGet(uri);
      HttpResponse result = client.execute(request, context);

      assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
      assertEquals(DefaultServer.getDefaultServerURL() + "/servletContext/", requestedUri(context, uri));
      Assert.assertTrue(HttpClientUtils.readResponse(result).startsWith("j_security_check"));
   }

   private String requestedUri(HttpClientContext context, String original) {
      if (context.getRedirectLocations() == null) {
         return original;
      }
      URI uri = context.getRedirectLocations().get(context.getRedirectLocations().size() - 1);
      return (uri == null)?original:uri.toString();

   }
}
