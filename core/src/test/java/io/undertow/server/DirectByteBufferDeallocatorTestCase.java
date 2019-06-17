/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
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
package io.undertow.server;

import org.junit.Test;

import java.security.Permission;
import java.security.Policy;
import java.security.ProtectionDomain;

import org.junit.Assert;

/**
 * Test for UNDERTOW-1558, it cannot instantiate the class if a security manager is set on JDK1.8
 *
 * @author tmiyar
 *
 */
public class DirectByteBufferDeallocatorTestCase {

    @Test
    public void directByteBufferDeallocatorInstantiationTest() {
        Exception exception = null;
        Policy.setPolicy(new Policy() {
            @Override
            public boolean implies(ProtectionDomain pd, Permission perm) {
                return true;
            }
        });
        System.setSecurityManager(new SecurityManager());
        try {
            DirectByteBufferDeallocator.free(null);
        } catch (Exception e) {
            exception = e;
        }

        Assert.assertNull("An exception was thrown with security manager enabled", exception);

        System.setSecurityManager(null);

        try {
            DirectByteBufferDeallocator.free(null);
        } catch (Exception e) {
            exception = e;
        }

        Assert.assertNull("An exception was thrown without security manager enabled", exception);
    }

}
