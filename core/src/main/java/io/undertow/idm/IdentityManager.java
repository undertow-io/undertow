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
package io.undertow.idm;

import java.util.Set;

/**
 * The IdentityManager interface to be implemented by an identity manager implementation providing user verification and
 * identity loading to Undertow.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public interface IdentityManager {

    Account lookupAccount(final String id);

    Account verifyCredential(final Credential credential);

    boolean verifyCredential(final Account account, final Credential credential);

    // TODO - Don't think this will remain but retaining for now.
    Set<String> getRoles(final Account account);

}
