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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server.handlers.proxy.mod_cluster;

import io.undertow.util.HttpString;

/**
 * @author Emanuel Muckenhuber
 */
interface MCMPConstants {

    String ALIAS_STRING = "Alias";
    String BALANCER_STRING = "Balancer";
    String CONTEXT_STRING = "Context";
    String DOMAIN_STRING = "Domain";
    String FLUSH_PACKET_STRING = "flushpackets";
    String FLUSH_WAIT_STRING = "flushwait";
    String HOST_STRING = "Host";
    String JVMROUTE_STRING = "JVMRoute";
    String LOAD_STRING = "Load";
    String MAXATTEMPTS_STRING = "Maxattempts";
    String PING_STRING = "ping";
    String PORT_STRING = "Port";
    String REVERSED_STRING = "Reversed";
    String SCHEME_STRING = "Scheme";
    String SMAX_STRING = "smax";
    String STICKYSESSION_STRING = "StickySession";
    String STICKYSESSIONCOOKIE_STRING = "StickySessionCookie";
    String STICKYSESSIONPATH_STRING = "StickySessionPath";
    String STICKYSESSIONREMOVE_STRING = "StickySessionRemove";
    String STICKYSESSIONFORCE_STRING = "StickySessionForce";
    String TIMEOUT_STRING = "Timeout";
    String TTL_STRING = "ttl";
    String TYPE_STRING = "Type";
    String WAITWORKER_STRING = "WaitWorker";

    HttpString ALIAS    = new HttpString(ALIAS_STRING);
    HttpString BALANCER = new HttpString(BALANCER_STRING);
    HttpString CONTEXT  = new HttpString(CONTEXT_STRING);
    HttpString DOMAIN   = new HttpString(DOMAIN_STRING);
    HttpString FLUSH_PACKET = new HttpString(FLUSH_PACKET_STRING);
    HttpString FLUSH_WAIT = new HttpString(FLUSH_WAIT_STRING);
    HttpString HOST     = new HttpString(HOST_STRING);
    HttpString JVMROUTE = new HttpString(JVMROUTE_STRING);
    HttpString LOAD     = new HttpString(LOAD_STRING);
    HttpString MAXATTEMPTS = new HttpString(MAXATTEMPTS_STRING);
    HttpString PING     = new HttpString(PING_STRING);
    HttpString PORT     = new HttpString(PORT_STRING);
    HttpString REVERSED = new HttpString(REVERSED_STRING);
    HttpString SCHEME   = new HttpString(SCHEME_STRING);
    HttpString SMAX     = new HttpString(SMAX_STRING);
    HttpString STICKYSESSION = new HttpString(STICKYSESSION_STRING);
    HttpString STICKYSESSIONCOOKIE = new HttpString(STICKYSESSIONCOOKIE_STRING);
    HttpString STICKYSESSIONPATH = new HttpString(STICKYSESSIONPATH_STRING);
    HttpString STICKYSESSIONREMOVE = new HttpString(STICKYSESSIONREMOVE_STRING);
    HttpString STICKYSESSIONFORCE = new HttpString(STICKYSESSIONFORCE_STRING);
    HttpString TIMEOUT  = new HttpString(TIMEOUT_STRING);
    HttpString TTL      = new HttpString(TTL_STRING);
    HttpString TYPE     = new HttpString(TYPE_STRING);
    HttpString WAITWORKER = new HttpString(WAITWORKER_STRING);

    String TYPESYNTAX = "SYNTAX";
    String TYPEMEM = "MEM";

}
