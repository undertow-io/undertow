package io.undertow.server.handlers.form;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

/**
 * Factory class that can create a form data parser for a given request.
 * <p/>
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

    public interface ParserDefinition {
        FormDataParser create(final HttpServerExchange exchange);
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

        private List<ParserDefinition> parsers = new ArrayList<ParserDefinition>();

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

        public FormParserFactory build() {
            return new FormParserFactory(parsers);
        }

    }

}
