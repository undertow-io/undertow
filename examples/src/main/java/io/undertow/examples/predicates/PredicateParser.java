/*
* JBoss, Home of Professional Open Source.
* Copyright 2023 Red Hat, Inc., and individual contributors
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
package io.undertow.examples.predicates;

import io.undertow.Undertow;
import io.undertow.examples.UndertowExample;
import io.undertow.predicate.PredicatesHandler;
import io.undertow.server.handlers.builder.PredicatedHandler;
import io.undertow.server.handlers.builder.PredicatedHandlersParser;
import io.undertow.util.Headers;

import java.util.List;

/**
 * @author Dimitris Kafetzis
 */
@UndertowExample("Predicate Parser")
public class PredicateParser {

    public static void main(final String[] args) {

        //The predicate parser accepts either a File, Path, InputStream or String containing the rules in order to generate the handlers and predicates.
        //In this case a redirect is executed when the path of the request starts with bar, which is then redirected to path foo
        List<PredicatedHandler> predicatedHandlers = PredicatedHandlersParser.parse("path-prefix('/bar') -> redirect(/foo);", io.undertow.examples.predicates.PredicateParser.class.getClassLoader());

        //A predicatesHandler is required along with a handler to be called in after all other handlers are done
        PredicatesHandler predicatesHandler = new PredicatesHandler(exchange -> {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            exchange.getResponseSender().send("Hello World from path: " + exchange.getRequestPath());
        });

        for (PredicatedHandler handler : predicatedHandlers) {
            predicatesHandler.addPredicatedHandler(handler);
        }
        Undertow server = Undertow.builder()
                .addHttpListener(8080, "localhost")
                .setHandler(predicatesHandler)
                .build();
        server.start();
    }
}
