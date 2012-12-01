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
package io.undertow.server.handlers.security;

import io.undertow.idm.Account;

import java.security.Principal;

/**
 * A Principal implementation to wrap the Account representation from the identity manager.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class UndertowPrincipal implements Principal {

    private final Account account;

    UndertowPrincipal(final Account account) {
        this.account = account;
    }

    /**
     *
     * @see java.security.Principal#getName()
     */
    @Override
    public String getName() {
        return account.getName();
    }

    Account getAccount() {
        return account;
    }

}
