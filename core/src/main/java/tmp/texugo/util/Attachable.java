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

package tmp.texugo.util;

import java.util.concurrent.ConcurrentMap;

/**
 * A thing which can have named attachments.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class Attachable {
    private final ConcurrentMap<String, Object> attachments = new SecureHashMap<String, Object>();

    public Object getAttachment(String name) {
        return attachments.get(name);
    }

    public Object putAttachment(String name, Object value) {
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        return attachments.put(name, value);
    }

    public Object putAttachmentIfAbsent(String name, Object value) {
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        return attachments.putIfAbsent(name, value);
    }

    public Object replaceAttachment(String name, Object newValue) {
        return attachments.replace(name, newValue);
    }

    public Object removeAttachment(String name) {
        return attachments.remove(name);
    }

    public boolean replaceAttachment(String name, Object expectValue, Object newValue) {
        return attachments.replace(name, expectValue, newValue);
    }

    public boolean removeAttachment(String name, Object expectValue) {
        return attachments.remove(name, expectValue);
    }

    public ConcurrentMap<String, Object> getAttachments() {
        return attachments;
    }
}
