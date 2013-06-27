package io.undertow.servlet.test;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Tomaz Cerar (c) 2013 Red Hat Inc.
 */
public class FailedTestCase {

    @Test
    public void testFailure(){
        Assert.fail("This should fail and be shown in github comment");
    }

    @Test
    public void testFailure2(){
        Assert.fail("Some other failure");
    }
}
