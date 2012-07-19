/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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

package tmp.texugo.util;

/**
 * Protocol version strings.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class Protocols {

    private Protocols() {
    }

    /**
     * HTTP 0.9.
     */
    public static final String HTTP_0_9 = "HTTP/0.9";
    /**
     * HTTP 1.0.
     */
    public static final String HTTP_1_0 = "HTTP/1.0";
    /**
     * HTTP 1.1.
     */
    public static final String HTTP_1_1 = "HTTP/1.1";
}
