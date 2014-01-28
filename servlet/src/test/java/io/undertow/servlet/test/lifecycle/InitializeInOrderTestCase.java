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
