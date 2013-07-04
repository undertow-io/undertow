package io.undertow.server.handlers.builder;

import io.undertow.UndertowMessages;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateParser;
import io.undertow.predicate.Predicates;
import io.undertow.server.HandlerWrapper;
import io.undertow.util.FileUtils;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for the undertow-handlers.conf file.
 * <p/>
 * This file has a line by line syntax, specifying predicate -> handler. If no predicate is specified then
 * the line is assumed to just contain a handler.
 *
 * @author Stuart Douglas
 */
public class PredicatedHandlersParser {


    public static List<PredicatedHandler> parse(final File file, final ClassLoader classLoader) {
        return parse(FileUtils.readFile(file), classLoader);
    }

    public static List<PredicatedHandler> parse(final InputStream inputStream, final ClassLoader classLoader) {
        return parse(FileUtils.readFile(inputStream), classLoader);
    }

    public static List<PredicatedHandler> parse(final String contents, final ClassLoader classLoader) {
        String[] lines = contents.split("\\n");
        final List<PredicatedHandler> wrappers = new ArrayList<PredicatedHandler>();

        for (String line : lines) {
            if (line.trim().length() > 0) {
                Predicate predicate;
                HandlerWrapper handler;
                String[] parts = line.split("->");
                if (parts.length == 2) {
                    predicate = PredicateParser.parse(parts[0], classLoader);
                    handler = HandlerParser.parse(parts[1], classLoader);
                } else if (parts.length == 1) {
                    predicate = Predicates.truePredicate();
                    handler = HandlerParser.parse(parts[0], classLoader);
                } else {
                    throw UndertowMessages.MESSAGES.invalidSyntax(line);
                }
                wrappers.add(new PredicatedHandler(predicate, handler));
            }
        }
        return wrappers;
    }

}
