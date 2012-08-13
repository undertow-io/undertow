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

package io.undertow.servlet;

import io.undertow.servlet.api.DeploymentManager;
import org.jboss.logging.Message;
import org.jboss.logging.MessageBundle;
import org.jboss.logging.Messages;

/**
 * messages start at 10000
 *
 * @author Stuart Douglas
 */
@MessageBundle(projectCode = "UNDERTOW")
public interface UndertowServletMessages {

    UndertowServletMessages MESSAGES = Messages.getBundle(UndertowServletMessages.class);

    @Message(id = 10000, value = "Cannot build servlet metadata, two servlets with same name specified")
    IllegalArgumentException twoServletsWithSameName();

    @Message(id = 10001, value = "%s cannot be null")
    IllegalArgumentException paramCannotBeNull(String param);

    @Message(id = 10002, value = "Deployments can only be removed when in undeployed state, but state was %s")
    IllegalStateException canOnlyRemoveDeploymentsWhenUndeployed(DeploymentManager.State state);

    @Message(id = 10003, value = "Cannot call getInputStream(), getReader() already called")
    IllegalStateException getReaderAlreadyCalled();

    @Message(id = 10004, value = "Cannot call getReader(), getInputStream() already called")
    IllegalStateException getInputStreamAlreadyCalled();

    @Message(id = 10005, value = "Cannot call getOutputStream(), getWriter() already called")
    IllegalStateException getWriterAlreadyCalled();

    @Message(id = 10006, value = "Cannot call getWriter(), getOutputStream() already called")
    IllegalStateException getOutputStreamAlreadyCalled();
}
