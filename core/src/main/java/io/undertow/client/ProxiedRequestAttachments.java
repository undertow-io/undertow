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

package io.undertow.client;

import io.undertow.util.AttachmentKey;

/**
 * Additional attachments that are specific to requests that are being proxied from one server to another
 *
 * @author Stuart Douglas
 */
public class ProxiedRequestAttachments {

    public static final AttachmentKey<String> REMOTE_ADDRESS = AttachmentKey.create(String.class);
    public static final AttachmentKey<String> REMOTE_HOST = AttachmentKey.create(String.class);
    public static final AttachmentKey<String> SERVER_NAME = AttachmentKey.create(String.class);
    public static final AttachmentKey<Integer> SERVER_PORT = AttachmentKey.create(Integer.class);
    public static final AttachmentKey<Boolean> IS_SSL = AttachmentKey.create(Boolean.class);

    public static final AttachmentKey<String> REMOTE_USER = AttachmentKey.create(String.class);
    public static final AttachmentKey<String> AUTH_TYPE = AttachmentKey.create(String.class);
    public static final AttachmentKey<String> ROUTE = AttachmentKey.create(String.class);
    public static final AttachmentKey<String> SSL_CERT = AttachmentKey.create(String.class);
    public static final AttachmentKey<String> SSL_CYPHER = AttachmentKey.create(String.class);
    public static final AttachmentKey<byte[]> SSL_SESSION_ID = AttachmentKey.create(byte[].class);
    public static final AttachmentKey<Integer> SSL_KEY_SIZE = AttachmentKey.create(Integer.class);
    public static final AttachmentKey<String> SECRET = AttachmentKey.create(String.class);

}
