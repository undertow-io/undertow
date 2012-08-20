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

package io.undertow.util;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import io.undertow.UndertowMessages;

/**
 * A thing which can have named attachments.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractAttachable implements Attachable {
    private final ConcurrentMap<Object, Object> attachments = new SecureHashMap<Object, Object>();

    @Override
    public Object getAttachment(String name) {
        return attachments.get(name);
    }

    @Override
    public Object putAttachment(String name, Object value) {
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        return attachments.put(name, value);
    }

    @Override
    public Object putAttachmentIfAbsent(String name, Object value) {
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        return attachments.putIfAbsent(name, value);
    }

    @Override
    public Object replaceAttachment(String name, Object newValue) {
        return attachments.replace(name, newValue);
    }

    @Override
    public Object removeAttachment(String name) {
        return attachments.remove(name);
    }

    @Override
    public boolean replaceAttachment(String name, Object expectValue, Object newValue) {
        return attachments.replace(name, expectValue, newValue);
    }

    @Override
    public boolean removeAttachment(String name, Object expectValue) {
        return attachments.remove(name, expectValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T getAttachment(final AttachmentKey<T> key) {
        if (key == null) {
            return null;
        }
        return key.cast(attachments.get(key));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> List<T> getAttachmentList(AttachmentKey<? extends List<T>> key) {
        if (key == null) {
            return null;
        }
        List<T> list = key.cast(attachments.get(key));
        if (list == null) {
            return Collections.emptyList();
        }
        return list;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T putAttachment(final AttachmentKey<T> key, final T value) {
        if (key == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull();
        }
        return key.cast(attachments.put(key, key.cast(value)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T putAttachmentIfAbsent(final AttachmentKey<T> key, final T value) {
        if (key == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull();
        }
        return key.cast(attachments.putIfAbsent(key, key.cast(value)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T removeAttachment(final AttachmentKey<T> key) {
        if (key == null) {
            return null;
        }
        return key.cast(attachments.remove(key));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> void addToAttachmentList(final AttachmentKey<AttachmentList<T>> key, final T value) {
        if (key != null) {
            final Map<Object, Object> attachments = this.attachments;
            final AttachmentList<T> list = key.cast(attachments.get(key));
            if (list == null) {
                final AttachmentList<T> newList = new AttachmentList<T>(((ListAttachmentKey<T>) key).getValueClass());
                attachments.put(key, newList);
                newList.add(value);
            } else {
                list.add(value);
            }
        }
    }
}
