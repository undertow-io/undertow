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

package io.undertow.util;

/**
 * @author Stuart Douglas
 */
public class ETag {

    private final boolean weak;
    private final String tag;

    public ETag(final boolean weak, final String tag) {
        this.weak = weak;
        this.tag = tag;
    }

    public boolean isWeak() {
        return weak;
    }

    public String getTag() {
        return tag;
    }

    @Override
    public String toString() {
        if(weak) {
            return "W/\"" + tag + "\"";
        } else {
            return "\"" + tag + "\"";
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final ETag eTag = (ETag) o;

        if (weak != eTag.weak) return false;
        if (tag != null ? !tag.equals(eTag.tag) : eTag.tag != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = (weak ? 1 : 0);
        result = 31 * result + (tag != null ? tag.hashCode() : 0);
        return result;
    }
}
