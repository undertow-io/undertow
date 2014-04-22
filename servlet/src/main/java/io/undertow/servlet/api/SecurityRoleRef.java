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

package io.undertow.servlet.api;

import io.undertow.servlet.UndertowServletMessages;

/**
 * @author Stuart Douglas
 */
public class SecurityRoleRef {

    private final String role;
    private final String linkedRole;

    public SecurityRoleRef(final String role, final String linkedRole) {
        if(role == null) {
            throw UndertowServletMessages.MESSAGES.paramCannotBeNull("role");
        }
        this.role = role;
        this.linkedRole = linkedRole;
    }

    public String getRole() {
        return role;
    }

    public String getLinkedRole() {
        return linkedRole;
    }
}
