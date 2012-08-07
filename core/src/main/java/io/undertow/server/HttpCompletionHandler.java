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

package io.undertow.server;

/**
 * The handler which is called when an {@link HttpHandler} has completely finished processing a request.  Calling
 * this handler will generally force the request and response streams to be cleaned and closed asynchronously as
 * appropriate.  The handler may be called from the same thread as the request handler's original execution, or a
 * different thread.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface HttpCompletionHandler {

    /**
     * Signify completion of the request handler's execution.
     */
    void handleComplete();
}
