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

package io.undertow.annotationprocessor;

import org.jboss.classfilewriter.ClassFile;
import org.jboss.classfilewriter.ClassMethod;
import org.jboss.classfilewriter.code.CodeAttribute;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Stuart Douglas
 * @author Emanuel Muckenhuber
 */
public class ResponseParserGenerator extends AbstractParserGenerator {

    //class names
    public static final String PARSE_STATE_CLASS = "io.undertow.client.http.ResponseParseState";
    public static final String HTTP_RESPONSE_CLASS = "io.undertow.client.http.HttpResponseBuilder";

    //parsing states
    public static final int VERSION = 0;
    public static final int STATUS_CODE = 1;
    public static final int REASON_PHRASE = 2;
    public static final int AFTER_REASON_PHRASE = 3;
    public static final int HEADER = 4;
    public static final int HEADER_VALUE = 5;
    public static final int PARSE_COMPLETE = 6;


    public ResponseParserGenerator(String existingClassName) {
        super(PARSE_STATE_CLASS, HTTP_RESPONSE_CLASS, "()V", existingClassName);
    }


    @Override
    protected void createStateMachines(final String[] httpVerbs, final String[] httpVersions, final String[] standardHeaders, final String className, final ClassFile file, final ClassMethod sctor, final AtomicInteger fieldCounter) {

        createStateMachine(httpVersions, className, file, sctor, fieldCounter, HANDLE_HTTP_VERSION, new VersionStateMachine());
        createStateMachine(standardHeaders, className, file, sctor, fieldCounter, HANDLE_HEADER, new HeaderStateMachine());
    }

    private static class HeaderStateMachine implements CustomStateMachine {

        @Override
        public boolean isHeader() {
            return true;
        }

        @Override
        public void handleOtherToken(final CodeAttribute c) {
            c.aload(PARSE_STATE_VAR);
            c.swap();
            c.putfield(PARSE_STATE_CLASS, "nextHeader", HTTP_STRING_DESCRIPTOR);
        }

        @Override
        public void handleStateMachineMatchedToken(final CodeAttribute c) {
            c.aload(PARSE_STATE_VAR);
            c.swap();
            c.putfield(PARSE_STATE_CLASS, "nextHeader", HTTP_STRING_DESCRIPTOR);
        }

        @Override
        public void updateParseState(final CodeAttribute c) {
            c.pop();
            c.aload(PARSE_STATE_VAR);
            c.iconst(HEADER_VALUE);
            c.putfield(PARSE_STATE_CLASS, "state", "I");
        }

        @Override
        public boolean initialNewlineMeansRequestDone() {
            return true;
        }
    }

    private static class VersionStateMachine implements CustomStateMachine {

        @Override
        public boolean isHeader() {
            return false;
        }

        @Override
        public void handleOtherToken(final CodeAttribute c) {
            c.aload(HTTP_RESULT);
            c.swap();
            c.invokevirtual(HTTP_RESPONSE_CLASS, "setProtocol", "(" + HTTP_STRING_DESCRIPTOR + ")V");
        }

        @Override
        public void handleStateMachineMatchedToken(final CodeAttribute c) {
            c.aload(HTTP_RESULT);
            c.swap();
            c.invokevirtual(HTTP_RESPONSE_CLASS, "setProtocol", "(" + HTTP_STRING_DESCRIPTOR + ")V");
        }

        @Override
        public void updateParseState(final CodeAttribute c) {
            c.aload(PARSE_STATE_VAR);
            c.swap();
            c.putfield(PARSE_STATE_CLASS, "leftOver", "B");
            c.aload(PARSE_STATE_VAR);
            c.iconst(STATUS_CODE);
            c.putfield(PARSE_STATE_CLASS, "state", "I");
        }

        @Override
        public boolean initialNewlineMeansRequestDone() {
            return false;
        }

    }
}
