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

package io.undertow.server.handlers.form;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

/**
 * Factory class that can create a form data parser for a given request.
 * <p>
 * It does this by iterating the available parser definitions, and returning
 * the first parser that is created.
 *
 * @author Stuart Douglas
 */
public class FormParserFactory {

    private static final AttachmentKey<FormDataParser> ATTACHMENT_KEY = AttachmentKey.create(FormDataParser.class);

    private final ParserDefinition[] parserDefinitions;

    FormParserFactory(final List<ParserDefinition> parserDefinitions) {
        this.parserDefinitions = parserDefinitions.toArray(new ParserDefinition[parserDefinitions.size()]);
    }

    /**
     * Creates a form data parser for this request. If a parser has already been created for the request the
     * existing parser will be returned rather than creating a new one.
     *
     * @param exchange The exchange
     * @return A form data parser, or null if there is no parser registered for the request content type
     */
    public FormDataParser createParser(final HttpServerExchange exchange) {
        FormDataParser existing = exchange.getAttachment(ATTACHMENT_KEY);
        if(existing != null) {
            return existing;
        }
        for (int i = 0; i < parserDefinitions.length; ++i) {
            FormDataParser parser = parserDefinitions[i].create(exchange);
            if (parser != null) {
                exchange.putAttachment(ATTACHMENT_KEY, parser);
                return parser;
            }
        }
        return null;
    }

    public interface ParserDefinition<T> {

        FormDataParser create(final HttpServerExchange exchange);

        T setDefaultEncoding(String charset);
    }

    public static Builder builder() {
        return builder(true);
    }

    public static Builder builder(boolean includeDefault) {
        Builder builder = new Builder();
        if (includeDefault) {
            builder.addParsers(new FormEncodedDataDefinition(), new MultiPartParserDefinition());
        }
        return builder;
    }

    public static class Builder {

        private List<ParserDefinition> parsers = new ArrayList<>();

        private String defaultCharset = null;

        public Builder addParser(final ParserDefinition definition) {
            parsers.add(definition);
            return this;
        }

        public Builder addParsers(final ParserDefinition... definition) {
            parsers.addAll(Arrays.asList(definition));
            return this;
        }

        public Builder addParsers(final List<ParserDefinition> definition) {
            parsers.addAll(definition);
            return this;
        }

        public List<ParserDefinition> getParsers() {
            return parsers;
        }

        public void setParsers(List<ParserDefinition> parsers) {
            this.parsers = parsers;
        }

        /**
         * A chainable version of {@link #setParsers}.
         */
        public Builder withParsers(List<ParserDefinition> parsers) {
            setParsers(parsers);
            return this;
        }

        public String getDefaultCharset() {
            return defaultCharset;
        }

        public void setDefaultCharset(String defaultCharset) {
            this.defaultCharset = defaultCharset;
        }

        /**
         * A chainable version of {@link #setDefaultCharset}.
         */
        public Builder withDefaultCharset(String defaultCharset) {
            setDefaultCharset(defaultCharset);
            return this;
        }

        public FormParserFactory build() {
            if(defaultCharset != null) {
                for (ParserDefinition parser : parsers) {
                    parser.setDefaultEncoding(defaultCharset);
                }
            }
            return new FormParserFactory(parsers);
        }

    }

}
