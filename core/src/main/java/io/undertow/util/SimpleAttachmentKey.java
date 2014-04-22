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
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
class SimpleAttachmentKey<T> extends AttachmentKey<T> {
    private final Class<T> valueClass;

    SimpleAttachmentKey(final Class<T> valueClass) {
        this.valueClass = valueClass;
    }

    public T cast(final Object value) {
        return valueClass.cast(value);
    }

    @Override
    public String toString() {
        if (valueClass != null) {
            StringBuilder sb = new StringBuilder(getClass().getName());
            sb.append("<");
            sb.append(valueClass.getName());
            sb.append(">");
            return sb.toString();
        }
        return super.toString();
    }
}
