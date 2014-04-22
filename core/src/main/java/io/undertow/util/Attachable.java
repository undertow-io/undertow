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

import java.util.List;

/**
 * A thing which can have named attachments.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface Attachable {

    /**
     * Get an attachment value.  If no attachment exists for this key, {@code null} is returned.
     *
     * @param key the attachment key
     * @param <T> the value type
     * @return the value, or {@code null} if there is none
     */
    <T> T getAttachment(AttachmentKey<T> key);

    /**
     * Gets a list attachment value. If not attachment exists for this key an empty list is returned
     *
     * @param <T> the value type
     * @param key the attachment key
     * @return the value, or an empty list if there is none
     */
    <T> List<T> getAttachmentList(AttachmentKey<? extends List<T>> key);

    /**
     * Set an attachment value. If an attachment for this key was already set, return the original value. If the value being set
     * is {@code null}, the attachment key is removed.
     *
     * @param key the attachment key
     * @param value the new value
     * @param <T> the value type
     * @return the old value, or {@code null} if there was none
     */
    <T> T putAttachment(AttachmentKey<T> key, T value);

    /**
     * Remove an attachment, returning its previous value.
     *
     * @param key the attachment key
     * @param <T> the value type
     * @return the old value, or {@code null} if there was none
     */
    <T> T removeAttachment(AttachmentKey<T> key);
    /**
     * Add a value to a list-typed attachment key.  If the key is not mapped, add such a mapping.
     *
     * @param key the attachment key
     * @param value the value to add
     * @param <T> the list value type
     */
    <T> void addToAttachmentList(AttachmentKey<AttachmentList<T>> key, T value);
}
