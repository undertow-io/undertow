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

package io.undertow.server.handlers.builder;

import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateParser;
import io.undertow.predicate.Predicates;
import io.undertow.server.HandlerWrapper;
import io.undertow.util.ChainedHandlerWrapper;
import io.undertow.util.FileUtils;
import io.undertow.util.PredicateTokeniser;
import io.undertow.util.PredicateTokeniser.Token;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Parser for the undertow-handlers.conf file.
 * <p>
 * This file has a line by line syntax, specifying predicate -&gt; handler. If no predicate is specified then
 * the line is assumed to just contain a handler.
 *
 * @author Stuart Douglas
 */
public class PredicatedHandlersParser {

    public static List<PredicatedHandler> parse(final File file, final ClassLoader classLoader) {
        return parse(file.toPath(), classLoader);
    }

    public static List<PredicatedHandler> parse(final Path file, final ClassLoader classLoader) {
        try {
            return parse(new String(Files.readAllBytes(file)), classLoader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<PredicatedHandler> parse(final InputStream inputStream, final ClassLoader classLoader) {
        return parse(FileUtils.readFile(inputStream), classLoader);
    }

    public static List<PredicatedHandler> parse(final String contents, final ClassLoader classLoader) {
        String[] lines = contents.split("\\n");
        final List<PredicatedHandler> wrappers = new ArrayList<>();

        Deque<Token> tokens = PredicateTokeniser.tokenize(contents);
        while (!tokens.isEmpty()) {
            List<Deque<Token>> others = new ArrayList<>();
            Predicate predicate;
            HandlerWrapper handler;
            Deque<Token> predicatePart = new ArrayDeque<>();
            Deque<Token> current = predicatePart;
            boolean done = false;
            while (!tokens.isEmpty() && !done) {
                Token token = tokens.poll();
                if (token.getToken().equals("->")) {
                    current = new ArrayDeque<>();
                    others.add(current);
                } else if(token.getToken().equals("\n")) {
                    done = true;
                } else {
                    current.add(token);
                }
            }
            if (others.isEmpty()) {
                predicate = Predicates.truePredicate();
                handler = HandlerParser.parse(contents, predicatePart, classLoader);
            } else if (others.size() == 1) {
                predicate = PredicateParser.parse(contents, predicatePart, classLoader);
                handler = HandlerParser.parse(contents, others.get(0), classLoader);
            } else {
                predicate = PredicateParser.parse(contents, predicatePart, classLoader);
                HandlerWrapper[] handlers = new HandlerWrapper[others.size()];
                for (int i = 0; i < handlers.length; ++i) {
                    handlers[i] = HandlerParser.parse(contents, others.get(i), classLoader);
                }
                handler = new ChainedHandlerWrapper(Arrays.asList(handlers));
            }
            wrappers.add(new PredicatedHandler(predicate, handler));
        }


        return wrappers;
    }

}
