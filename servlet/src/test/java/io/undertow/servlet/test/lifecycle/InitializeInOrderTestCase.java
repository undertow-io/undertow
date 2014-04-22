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

package io.undertow.servlet.test.lifecycle;

import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.DeploymentUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Stuart Douglas
 */
public class InitializeInOrderTestCase {


    @BeforeClass
    public static void setup() {
        DeploymentUtils.setupServlet(new ServletInfo("s1", FirstServlet.class)
                .setLoadOnStartup(1),
                new ServletInfo("s2", SecondServlet.class)
                        .setLoadOnStartup(2));
    }

    @Test
    public void testInitializeInOrder() throws Exception {
        Assert.assertTrue(FirstServlet.init);
        Assert.assertTrue(SecondServlet.init);
    }
}
