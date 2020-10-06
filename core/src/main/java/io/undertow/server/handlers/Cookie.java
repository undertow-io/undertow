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

package io.undertow.server.handlers;

import java.util.Date;

/**
 * A HTTP cookie.
 *
 * @see io.undertow.server.Connectors
 * @author Stuart Douglas
 */
public interface Cookie extends Comparable {

    String getName();

    String getValue();

    Cookie setValue(final String value);

    String getPath();

    Cookie setPath(final String path);

    String getDomain();

    Cookie setDomain(final String domain);

    Integer getMaxAge();

    Cookie setMaxAge(final Integer maxAge);

    boolean isDiscard();

    Cookie setDiscard(final boolean discard);

    boolean isSecure();

    Cookie setSecure(final boolean secure);

    int getVersion();

    Cookie setVersion(final int version);

    boolean isHttpOnly();

    Cookie setHttpOnly(final boolean httpOnly);

    Date getExpires();

    Cookie setExpires(final Date expires);

    String getComment();

    Cookie setComment(final String comment);

    default boolean isSameSite() {
        return false;
    }

    default Cookie setSameSite(final boolean sameSite) {
        throw new UnsupportedOperationException("Not implemented");
    }

    default String getSameSiteMode() {
        return null;
    }

    default Cookie setSameSiteMode(final String mode) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    default int compareTo(final Object other) {
        final Cookie o = (Cookie) other;
        int retVal = 0;

        // compare names
        if (getName() == null && o.getName() != null) return -1;
        if (getName() != null && o.getName() == null) return 1;
        retVal = (getName() == null && o.getName() == null) ? 0 : getName().compareTo(o.getName());
        if (retVal != 0) return retVal;

        // compare paths
        if (getPath() == null && o.getPath() != null) return -1;
        if (getPath() != null && o.getPath() == null) return 1;
        retVal = (getPath() == null && o.getPath() == null) ? 0 : getPath().compareTo(o.getPath());
        if (retVal != 0) return retVal;

        // compare domains
        if (getDomain() == null && o.getDomain() != null) return -1;
        if (getDomain() != null && o.getDomain() == null) return 1;
        retVal = (getDomain() == null && o.getDomain() == null) ? 0 : getDomain().compareTo(o.getDomain());
        if (retVal != 0) return retVal;

        return 0; // equal
    }

}
