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

package io.undertow.predicate;

import io.undertow.server.handlers.builder.PredicatedHandlersParser;

/**
 * Parser that can build a predicate from a string representation. The underlying syntax is quite simple, and example is
 * shown below:
 * <p>
 * <code>
 * path["/MyPath"] or (method[value="POST"] and not headersPresent[value={Content-Type, "Content-Encoding"}, ignoreTrailer=true]
 * </code>
 * <p>
 * The following boolean operators are built in, listed in order or precedence:
 * - not
 * - and
 * - or
 * <p>
 * They work pretty much as you would expect them to. All other tokens are taken
 * to be predicate names. If the predicate does not require any parameters then the
 * brackets can be omitted, otherwise they are mandatory.
 * <p>
 * If a predicate is only being passed a single parameter then the parameter name can be omitted.
 * Strings can be enclosed in optional double or single quotations marks, and quotation marks can be escaped using
 * <code>\"</code>.
 * <p>
 * Array types are represented via a comma separated list of values enclosed in curly braces.
 * <p>
 * TODO: should we use antlr (or whatever) here? I don't really want an extra dependency just for this...
 *
 * @author Stuart Douglas
 */
public class PredicateParser {

    public static final Predicate parse(String string, final ClassLoader classLoader) {
        return PredicatedHandlersParser.parsePredicate(string, classLoader);
    }

}
