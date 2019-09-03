/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
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

public enum CookieSameSiteMode {

    /**
     * The browser will only send cookies for same-site requests (requests originating from the site that set the cookie).
     * If the request originated from a different URL than the URL of the current location, none of the cookies tagged
     * with the Strict attribute will be included.
     */
    STRICT("Strict"),

    /**
     * Same-site cookies are withheld on cross-site subrequests, such as calls to load images or frames, but will be sent
     * when a user navigates to the URL from an external site; for example, by following a link.
     */
    LAX("Lax"),

    /**
     * The browser will send cookies with both cross-site requests and same-site requests.
     */
    NONE("None");

    private static final CookieSameSiteMode[] SAMESITE_MODES = values();

    /**
     * Just use a human friendly label instead of the capitalized name.
     */
    private final String label;

    CookieSameSiteMode(String label) {
        this.label = label;
    }

    /**
     * lookup from the specified value and return a correct SameSiteMode string.
     *
     * @param mode
     * @return A name of SameSite mode. Returns {@code null} if an invalid SameSite mode is specified.
     */
    public static String lookupModeString(String mode) {
        for (CookieSameSiteMode m : SAMESITE_MODES) {
            if (m.name().equalsIgnoreCase(mode)) {
                return m.toString();
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return label;
    }

}
