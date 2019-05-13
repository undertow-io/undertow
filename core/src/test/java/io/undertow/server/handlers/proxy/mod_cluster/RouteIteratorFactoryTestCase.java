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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.server.handlers.proxy.mod_cluster;

import static io.undertow.server.handlers.proxy.RouteIteratorFactory.ParsingCompatibility.MOD_CLUSTER;
import static io.undertow.server.handlers.proxy.RouteIteratorFactory.ParsingCompatibility.MOD_JK;
import static io.undertow.server.handlers.proxy.RouteParsingStrategy.NONE;
import static io.undertow.server.handlers.proxy.RouteParsingStrategy.SINGLE;
import static io.undertow.server.handlers.proxy.RouteParsingStrategy.RANKED;

import java.util.Iterator;

import io.undertow.server.handlers.proxy.RouteIteratorFactory;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests route/affinity parsing mechanism including ranked routing.
 *
 * @author Radoslav Husar
 */
public class RouteIteratorFactoryTestCase {

    @Test
    public void testModJkLikeRouteParsing() {
        // Disabled sticky sessions on the load balancer
        Iterator<CharSequence> ri = new RouteIteratorFactory(NONE, MOD_JK).iterator("mKaJwtWjqgxFbSSlaKZeGly_RMPKCg13JXe-6R_h.node1.domain");
        Assert.assertFalse(ri.hasNext());

        // Default behavior
        ri = new RouteIteratorFactory(SINGLE, MOD_JK).iterator("mKaJwtWjqgxFbSSlaKZeGly_RMPKCg13JXe-6R_h");
        Assert.assertFalse(ri.hasNext());

        // No ranked routing support taking as route only between first "." and second "."
        ri = new RouteIteratorFactory(SINGLE, MOD_JK).iterator("mKaJwtWjqgxFbSSlaKZeGly_RMPKCg13JXe-6R_h.node1.domain1.something");
        Assert.assertTrue(ri.hasNext());
        Assert.assertEquals("node1", ri.next().toString());
        Assert.assertFalse(ri.hasNext());

        // Ranked routing support but no route given
        ri = new RouteIteratorFactory(RANKED, MOD_JK, ".").iterator("mKaJwtWjqgxFbSSlaKZeGly_RMPKCg13JXe-6R_h");
        Assert.assertFalse(ri.hasNext());

        // Multi-route support with the same character delimiter as sessionID delimiter '.' -- overriding domain support parsing
        ri = new RouteIteratorFactory(RANKED, MOD_JK, ".").iterator("mKaJwtWjqgxFbSSlaKZeGly_RMPKCg13JXe-6R_h.node1.node2.node3");
        Assert.assertTrue(ri.hasNext());
        Assert.assertEquals("node1", ri.next().toString());
        Assert.assertTrue(ri.hasNext());
        Assert.assertEquals("node2", ri.next().toString());
        Assert.assertTrue(ri.hasNext());
        Assert.assertEquals("node3", ri.next().toString());
        Assert.assertFalse(ri.hasNext());
    }

    @Test
    public void testModClusterRouteParsing() {
        // Disabled sticky sessions
        Iterator<CharSequence> ri = new RouteIteratorFactory(NONE, MOD_CLUSTER).iterator("mKaJwtWjqgxFbSSlaKZeGly_RMPKCg13JXe-6R_h");
        Assert.assertFalse(ri.hasNext());

        ri = new RouteIteratorFactory(NONE, MOD_CLUSTER).iterator("mKaJwtWjqgxFbSSlaKZeGly_RMPKCg13JXe-6R_h.node1.node2");
        Assert.assertFalse(ri.hasNext());

        // No ranked routing support and no route given or null sessionId
        ri = new RouteIteratorFactory(SINGLE, MOD_CLUSTER).iterator("mKaJwtWjqgxFbSSlaKZeGly_RMPKCg13JXe-6R_h");
        Assert.assertFalse(ri.hasNext());

        ri = new RouteIteratorFactory(SINGLE, MOD_CLUSTER).iterator(null);
        Assert.assertFalse(ri.hasNext());

        // No ranked routing support treating everything after '.' as route
        ri = new RouteIteratorFactory(SINGLE, MOD_CLUSTER).iterator("mKaJwtWjqgxFbSSlaKZeGly_RMPKCg13JXe-6R_h.node1.node2.node3");
        Assert.assertTrue(ri.hasNext());
        Assert.assertEquals("node1.node2.node3", ri.next().toString());
        Assert.assertFalse(ri.hasNext());

        // Ranked routing support but no route given
        ri = new RouteIteratorFactory(RANKED, MOD_CLUSTER, ".").iterator("mKaJwtWjqgxFbSSlaKZeGly_RMPKCg13JXe-6R_h");
        Assert.assertFalse(ri.hasNext());

        // Multi-route support with the same character delimiter as sessionID delimiter '.'
        ri = new RouteIteratorFactory(RANKED, MOD_CLUSTER, ".").iterator("mKaJwtWjqgxFbSSlaKZeGly_RMPKCg13JXe-6R_h.node1.node2.node3");
        Assert.assertTrue(ri.hasNext());
        Assert.assertEquals("node1", ri.next().toString());
        Assert.assertTrue(ri.hasNext());
        Assert.assertEquals("node2", ri.next().toString());
        Assert.assertTrue(ri.hasNext());
        Assert.assertEquals("node3", ri.next().toString());
        Assert.assertFalse(ri.hasNext());

        // Multi-route support with a different character delimiter ':'
        ri = new RouteIteratorFactory(RANKED, MOD_CLUSTER, ":").iterator("mKaJwtWjqgxFbSSlaKZeGly_RMPKCg13JXe-6R_h.node1:node2.1:node3.1");
        Assert.assertTrue(ri.hasNext());
        Assert.assertEquals("node1", ri.next().toString());
        Assert.assertTrue(ri.hasNext());
        Assert.assertEquals("node2.1", ri.next().toString());
        Assert.assertTrue(ri.hasNext());
        Assert.assertEquals("node3.1", ri.next().toString());
        Assert.assertFalse(ri.hasNext());

        // Multi-route support with messy inputs
        ri = new RouteIteratorFactory(RANKED, MOD_CLUSTER, ":").iterator("mKaJwtWjqgxFbSSlaKZeGly_RMPKCg13JXe-6R_h.node1::node2::");
        Assert.assertTrue(ri.hasNext());
        Assert.assertEquals("node1", ri.next().toString());
        Assert.assertTrue(ri.hasNext());
        Assert.assertEquals("", ri.next().toString());
        Assert.assertTrue(ri.hasNext());
        Assert.assertEquals("node2", ri.next().toString());
        Assert.assertTrue(ri.hasNext());
        Assert.assertEquals("", ri.next().toString());
        Assert.assertFalse(ri.hasNext());

        // Multi-route multi-character delimiter support
        ri = new RouteIteratorFactory(RANKED, MOD_CLUSTER, "|||").iterator("mKaJwtWjqgxFbSSlaKZeGly_RMPKCg13JXe-6R_h.node1|||node2|||node3");
        Assert.assertTrue(ri.hasNext());
        Assert.assertEquals("node1", ri.next().toString());
        Assert.assertTrue(ri.hasNext());
        Assert.assertEquals("node2", ri.next().toString());
        Assert.assertTrue(ri.hasNext());
        Assert.assertEquals("node3", ri.next().toString());
        Assert.assertFalse(ri.hasNext());
    }
}
