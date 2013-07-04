package io.undertow.server.handlers.builder;

import io.undertow.UndertowMessages;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateParser;
import io.undertow.predicate.Predicates;
import io.undertow.predicate.PredicatesHandler;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.util.FileUtils;

import java.io.File;
import java.io.InputStream;

/**
 * Parser for the undertow-handlers.conf file.
 * <p/>
 * This file has a line by line syntax, specifying predicate -> handler. If no predicate is specified then
 * the line is assumed to just contain a handler.
 *
 * @author Stuart Douglas
 */
public class PredicatedHandlersParser {


    public static PredicatesHandler parse(final File file, final ClassLoader classLoader, final HttpHandler next) {
        return parse(FileUtils.readFile(file), classLoader, next);
    }

    public static PredicatesHandler parse(final InputStream inputStream, final ClassLoader classLoader, final HttpHandler next) {
        return parse(FileUtils.readFile(inputStream), classLoader, next);
    }

    public static PredicatesHandler parse(final String contents, final ClassLoader classLoader, final HttpHandler next) {
        String[] lines = contents.split("\\n");
        PredicatesHandler predicatesHandler = new PredicatesHandler(next);

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
                predicatesHandler.addPredicatedHandler(predicate, handler);
            }
        }
        return predicatesHandler;
    }

}
