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

package io.undertow.servlet.predicate;

import io.undertow.attribute.ExchangeAttribute;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateBuilder;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.Resource;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.servlet.handlers.ServletRequestContext;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Predicate that returns true if the given location corresponds to a regular file.
 *
 * @author Stuart Douglas
 */
public class FilePredicate implements Predicate {

    private final ExchangeAttribute location;
    private final boolean requireContent;

    public FilePredicate(final ExchangeAttribute location) {
        this(location, false);
    }

    public FilePredicate(final ExchangeAttribute location, boolean requireContent) {
        this.location = location;
        this.requireContent = requireContent;
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        String location = this.location.readAttribute(value);
        ServletRequestContext src = value.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        if(src == null) {
            return false;
        }
        ResourceManager manager = src.getDeployment().getDeploymentInfo().getResourceManager();
        if(manager == null) {
            return false;
        }
        try {
            Resource resource = manager.getResource(location);
            if(resource == null) {
                return false;
            }
            if(resource.isDirectory()) {
                return false;
            }
            if(requireContent){
              return resource.getContentLength() != null && resource.getContentLength() > 0;
            } else {
                return true;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return "file( " + location.toString() + " )";
    }

    public static class Builder implements PredicateBuilder {

        @Override
        public String name() {
            return "file";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            final Map<String, Class<?>> params = new HashMap<>();
            params.put("value", ExchangeAttribute.class);
            params.put("require-content", Boolean.class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.emptySet();
        }

        @Override
        public String defaultParameter() {
            return "value";
        }

        @Override
        public Predicate build(final Map<String, Object> config) {
            ExchangeAttribute value = (ExchangeAttribute) config.get("value");
            Boolean requireContent = (Boolean)config.get("require-content");
            if(value == null) {
                value = ExchangeAttributes.relativePath();
            }
            return new FilePredicate(value, requireContent == null ? false : requireContent);
        }

        @Override
        public int priority() {
            return 0;
        }
    }

}
