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

import org.jboss.classfilewriter.AccessFlag;
import org.jboss.classfilewriter.ClassFile;
import org.jboss.classfilewriter.ClassMethod;
import org.jboss.classfilewriter.code.BranchEnd;
import org.jboss.classfilewriter.code.CodeAttribute;
import org.jboss.classfilewriter.code.CodeLocation;
import org.jboss.classfilewriter.code.TableSwitchBuilder;
import org.jboss.classfilewriter.util.DescriptorUtils;

import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Stuart Douglas
 */
public abstract class AbstractParserGenerator {

    public static final String BAD_REQUEST_EXCEPTION = "io.undertow.util.BadRequestException";
    //class names
    protected final String parseStateClass;
    protected String resultClass;
    protected final String constructorDescriptor;

    private final String parseStateDescriptor;
    private final String httpExchangeDescriptor;
    private final String existingClassName;

    public static final String HTTP_STRING_CLASS = "io.undertow.util.HttpString";
    public static final String HTTP_STRING_DESCRIPTOR = DescriptorUtils.makeDescriptor(HTTP_STRING_CLASS);


    //state machine states
    public static final int NO_STATE = -1;
    public static final int PREFIX_MATCH = -2;

    private static final int CONSTRUCTOR_HTTP_STRING_MAP_VAR = 1;

    protected static final int BYTE_BUFFER_VAR = 1;
    protected static final int PARSE_STATE_VAR = 2;
    protected static final int HTTP_RESULT = 3;
    protected static final int CURRENT_STATE_VAR = 4;
    protected static final int STATE_POS_VAR = 5;
    protected static final int STATE_CURRENT_VAR = 6;
    protected static final int STATE_STRING_BUILDER_VAR = 7;
    protected static final int STATE_CURRENT_BYTES_VAR = 8;

    public static final String HANDLE_HTTP_VERB = "handleHttpVerb";
    public static final String HANDLE_PATH = "handlePath";
    public static final String HANDLE_HTTP_VERSION = "handleHttpVersion";
    public static final String HANDLE_AFTER_VERSION = "handleAfterVersion";
    public static final String HANDLE_HEADER = "handleHeader";
    public static final String HANDLE_HEADER_VALUE = "handleHeaderValue";
    public static final String CLASS_NAME_SUFFIX = "$$generated";

    public AbstractParserGenerator(final String parseStateClass, final String resultClass, final String constructorDescriptor, String existingClassName) {
        this.parseStateClass = parseStateClass;
        this.resultClass = resultClass;
        this.existingClassName = existingClassName;
        parseStateDescriptor = DescriptorUtils.makeDescriptor(parseStateClass);
        httpExchangeDescriptor = DescriptorUtils.makeDescriptor(resultClass);
        this.constructorDescriptor = constructorDescriptor;
    }

    public byte[] createTokenizer(final String[] httpVerbs, String[] httpVersions, String[] standardHeaders) {
        final String className = existingClassName + CLASS_NAME_SUFFIX;
        final ClassFile file = new ClassFile(className, existingClassName);

        final ClassMethod ctor = file.addMethod(AccessFlag.PUBLIC, "<init>", "V", DescriptorUtils.parameterDescriptors(constructorDescriptor));
        ctor.getCodeAttribute().aload(0);
        ctor.getCodeAttribute().loadMethodParameters();
        ctor.getCodeAttribute().invokespecial(existingClassName, "<init>", constructorDescriptor);
        ctor.getCodeAttribute().returnInstruction();


        final ClassMethod sctor = file.addMethod(AccessFlag.PUBLIC | AccessFlag.STATIC, "<clinit>", "V");
        final AtomicInteger fieldCounter = new AtomicInteger(1);
        sctor.getCodeAttribute().invokestatic(existingClassName, "httpStrings", "()" + DescriptorUtils.makeDescriptor(Map.class));
        sctor.getCodeAttribute().astore(CONSTRUCTOR_HTTP_STRING_MAP_VAR);

        createStateMachines(httpVerbs, httpVersions, standardHeaders, className, file, sctor, fieldCounter);

        sctor.getCodeAttribute().returnInstruction();
        return file.toBytecode();
    }

    protected abstract void createStateMachines(String[] httpVerbs, String[] httpVersions, String[] standardHeaders, String className, ClassFile file, ClassMethod sctor, AtomicInteger fieldCounter);

    protected void createStateMachine(final String[] originalItems, final String className, final ClassFile file, final ClassMethod sctor, final AtomicInteger fieldCounter, final String methodName, final CustomStateMachine stateMachine, boolean expectNewline) {

        //list of all states except the initial
        final List<State> allStates = new ArrayList<>();
        final State initial = new State((byte) 0, "");
        for (String value : originalItems) {
            addStates(initial, value, allStates);
        }
        //we want initial to be number 0
        final AtomicInteger stateCounter = new AtomicInteger(-1);
        setupStateNo(initial, stateCounter, fieldCounter);
        for (State state : allStates) {
            setupStateNo(state, stateCounter, fieldCounter);
            createStateField(state, file, sctor.getCodeAttribute());
        }

        final int noStates = stateCounter.get();

        final ClassMethod handle = file.addMethod(Modifier.PROTECTED | Modifier.FINAL, methodName, "V", DescriptorUtils.makeDescriptor(ByteBuffer.class), parseStateDescriptor, httpExchangeDescriptor);
        handle.addCheckedExceptions(BAD_REQUEST_EXCEPTION);
        writeStateMachine(className, file, handle.getCodeAttribute(), initial, allStates, noStates, stateMachine, expectNewline);
    }

    private void createStateField(final State state, final ClassFile file, final CodeAttribute sc) {
        if (state.fieldName != null) {
            file.addField(AccessFlag.STATIC | AccessFlag.FINAL | AccessFlag.PRIVATE, state.fieldName, "[B");
            sc.ldc(state.terminalState);
            sc.ldc("ISO-8859-1");
            sc.invokevirtual(String.class.getName(), "getBytes", "(Ljava/lang/String;)[B");
            sc.putstatic(file.getName(), state.fieldName, "[B");
        }
        if (state.httpStringFieldName != null) {
            file.addField(AccessFlag.STATIC | AccessFlag.FINAL | AccessFlag.PRIVATE, state.httpStringFieldName, HTTP_STRING_DESCRIPTOR);

            //first we try and get the string from the map of known HTTP strings
            //this means that the result we store will be the same object as the
            //constants that are referenced in the handlers
            //if this fails we just create a new http string
            sc.aload(CONSTRUCTOR_HTTP_STRING_MAP_VAR);
            if (state.terminalState != null) {
                sc.ldc(state.terminalState);
            } else {
                sc.ldc(state.soFar);
            }
            sc.invokeinterface(Map.class.getName(), "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
            sc.dup();
            BranchEnd end = sc.ifnull();
            sc.checkcast(HTTP_STRING_CLASS);
            sc.putstatic(file.getName(), state.httpStringFieldName, HTTP_STRING_DESCRIPTOR);
            BranchEnd done = sc.gotoInstruction();
            sc.branchEnd(end);
            sc.pop();
            sc.newInstruction(HTTP_STRING_CLASS);
            sc.dup();
            if (state.terminalState != null) {
                sc.ldc(state.terminalState);
            } else {
                sc.ldc(state.soFar);
            }
            sc.invokespecial(HTTP_STRING_CLASS, "<init>", "(Ljava/lang/String;)V");
            sc.putstatic(file.getName(), state.httpStringFieldName, HTTP_STRING_DESCRIPTOR);
            sc.branchEnd(done);
        }
    }


    private void setupStateNo(final State state, final AtomicInteger stateCounter, final AtomicInteger fieldCounter) {
        if (state.next.isEmpty()) {
            state.stateno = PREFIX_MATCH;
            state.terminalState = state.soFar;
            state.fieldName = "STATE_BYTES_" + fieldCounter.incrementAndGet();
        } else if (state.next.size() == 1) {
            String terminal = null;
            State s = state.next.values().iterator().next();
            while (true) {
                if (s.next.size() > 1) {
                    break;
                } else if (s.next.isEmpty()) {
                    terminal = s.soFar;
                    break;
                }
                s = s.next.values().iterator().next();
            }
            if (terminal != null) {
                state.stateno = PREFIX_MATCH;
                state.terminalState = terminal;
                state.fieldName = "STATE_BYTES_" + fieldCounter.incrementAndGet();
            } else {
                state.stateno = stateCounter.incrementAndGet();
            }
        } else {
            state.stateno = stateCounter.incrementAndGet();
        }
        state.httpStringFieldName = "HTTP_STRING_" + fieldCounter.incrementAndGet();
    }

    private void writeStateMachine(final String className, final ClassFile file, final CodeAttribute c, final State initial, final List<State> allStates, int noStates, final CustomStateMachine stateMachine, boolean expectNewline) {

        //initial hasRemaining check
        c.aload(BYTE_BUFFER_VAR);
        c.invokevirtual(ByteBuffer.class.getName(), "hasRemaining", "()Z");
        final BranchEnd nonZero = c.ifne();
        //we have run out of bytes, return 0
        c.iconst(0);
        c.returnInstruction();

        c.branchEnd(nonZero);


        final List<State> states = new ArrayList<>();
        states.add(initial);
        states.addAll(allStates);
        Collections.sort(states);

        //store the current state in a local variable
        c.aload(PARSE_STATE_VAR);
        c.dup();
        c.getfield(parseStateClass, "stringBuilder", DescriptorUtils.makeDescriptor(StringBuilder.class));
        c.astore(STATE_STRING_BUILDER_VAR);
        c.dup();
        c.getfield(parseStateClass, "parseState", "I");
        c.dup();
        c.istore(CURRENT_STATE_VAR);
        //if this is state 0 there is a lot of stuff can ignore
        BranchEnd optimizationEnd = c.ifeq();
        c.dup();
        c.getfield(parseStateClass, "pos", "I");
        c.istore(STATE_POS_VAR);
        c.dup();
        c.getfield(parseStateClass, "current", HTTP_STRING_DESCRIPTOR);
        c.astore(STATE_CURRENT_VAR);
        c.getfield(parseStateClass, "currentBytes", "[B");
        c.astore(STATE_CURRENT_BYTES_VAR);


        //load the current state
        c.iload(CURRENT_STATE_VAR);
        //switch on the current state
        TableSwitchBuilder builder = new TableSwitchBuilder(-2, noStates);
        final IdentityHashMap<State, AtomicReference<BranchEnd>> ends = new IdentityHashMap<>();
        final AtomicReference<BranchEnd> prefixMatch = builder.add();
        final AtomicReference<BranchEnd> noState = builder.add();

        ends.put(initial, builder.add());
        for (final State s : states) {
            if (s.stateno > 0) {
                ends.put(s, builder.add());
            }
        }
        c.tableswitch(builder);
        stateNotFound(c, builder);

        //return code
        //code that synchronizes the state object and returns
        setupLocalVariables(c);
        final CodeLocation returnIncompleteCode = c.mark();
        c.aload(PARSE_STATE_VAR);
        c.dup();
        c.dup();
        c.dup();
        c.dup();

        c.iload(STATE_POS_VAR);
        c.putfield(parseStateClass, "pos", "I");
        c.aload(STATE_CURRENT_VAR);
        c.putfield(parseStateClass, "current", HTTP_STRING_DESCRIPTOR);
        c.aload(STATE_CURRENT_BYTES_VAR);
        c.putfield(parseStateClass, "currentBytes", "[B");
        c.iload(CURRENT_STATE_VAR);
        c.putfield(parseStateClass, "parseState", "I");
        c.returnInstruction();
        setupLocalVariables(c);
        final CodeLocation returnCompleteCode = c.mark();
        c.aload(PARSE_STATE_VAR);
        c.dup();
        c.dup();
        c.dup();
        c.dup();

        c.iconst(0);
        c.putfield(parseStateClass, "pos", "I");
        c.aconstNull();
        c.putfield(parseStateClass, "current", HTTP_STRING_DESCRIPTOR);
        c.aconstNull();
        c.putfield(parseStateClass, "currentBytes", "[B");
        c.aload(STATE_STRING_BUILDER_VAR);
        c.iconst(0);
        c.invokevirtual(StringBuilder.class.getName(), "setLength", "(I)V");
        c.iconst(0);
        c.putfield(parseStateClass, "parseState", "I");
        c.returnInstruction();

        //prefix
        c.branchEnd(prefixMatch.get());

        final CodeLocation prefixLoop = c.mark(); //loop for when we are prefix matching
        handleReturnIfNoMoreBytes(c, returnIncompleteCode);
        //load 3 copies of the current byte into the stack
        c.aload(BYTE_BUFFER_VAR);
        c.invokevirtual(ByteBuffer.class.getName(), "get", "()B");
        c.dup();
        c.dup();
        final Set<BranchEnd> prefixHandleSpace = new LinkedHashSet<>();
        final Set<BranchEnd> badPrefixHandleSpace = new LinkedHashSet<>();
        if (stateMachine.isHeader()) {
            c.iconst(':');
            prefixHandleSpace.add(c.ifIcmpeq());
            c.dup();
            c.iconst(' ');
            badPrefixHandleSpace.add(c.ifIcmpeq());
            c.dup();
            c.iconst('\r');
            prefixHandleSpace.add(c.ifIcmpeq());
            c.dup();
            c.iconst('\n');
            prefixHandleSpace.add(c.ifIcmpeq());
        }else if(!expectNewline) {
            c.iconst(' ');
            prefixHandleSpace.add(c.ifIcmpeq());
            c.dup();
            c.iconst('\r');
            badPrefixHandleSpace.add(c.ifIcmpeq());
            c.dup();
            c.iconst('\n');
            badPrefixHandleSpace.add(c.ifIcmpeq());
        } else {
            c.iconst('\r');
            prefixHandleSpace.add(c.ifIcmpeq());
            c.dup();
            c.iconst('\n');
            prefixHandleSpace.add(c.ifIcmpeq());
        }
        //check if we have overrun
        c.aload(STATE_CURRENT_BYTES_VAR);
        c.arraylength();
        c.iload(STATE_POS_VAR);
        BranchEnd overrun = c.ifIcmpeq();
        //so we have not overrun
        //now check if the character matches
        c.dup();
        c.aload(STATE_CURRENT_BYTES_VAR);
        c.iload(STATE_POS_VAR);
        c.baload();
        c.isub();
        BranchEnd noMatch = c.ifne();

        //so they match
        c.pop2(); //pop our extra bytes off the stack, we do not need it
        c.iinc(STATE_POS_VAR, 1);
        handleReturnIfNoMoreBytes(c, returnIncompleteCode);
        c.gotoInstruction(prefixLoop);

        c.branchEnd(overrun); //overrun and not match use the same code path
        c.branchEnd(noMatch); //the current character did not match
        c.iconst(NO_STATE);
        c.istore(CURRENT_STATE_VAR);

        //create the string builder
        c.aload(STATE_STRING_BUILDER_VAR);
        c.aload(STATE_CURRENT_VAR);
        c.invokevirtual(HTTP_STRING_CLASS, "toString", "()Ljava/lang/String;");
        c.iconst(0);
        c.iload(STATE_POS_VAR);
        c.invokevirtual(String.class.getName(), "substring", "(II)Ljava/lang/String;");
        c.invokevirtual(StringBuilder.class.getName(), "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        c.swap();

        c.invokevirtual(StringBuilder.class.getName(), "append", "(C)Ljava/lang/StringBuilder;");
        c.pop2();
        BranchEnd prefixToNoState = c.gotoInstruction();

        if(!badPrefixHandleSpace.isEmpty()) {
            //handle the space case
            for (BranchEnd b : badPrefixHandleSpace) {
                c.branchEnd(b);
            }
            c.newInstruction(BAD_REQUEST_EXCEPTION);
            c.dup();
            c.invokespecial(BAD_REQUEST_EXCEPTION, "<init>", "()V");
            c.athrow();
        }
        //handle the space case
        for (BranchEnd b : prefixHandleSpace) {
            c.branchEnd(b);
        }

        //new state will be 0
        c.iconst(0);
        c.istore(CURRENT_STATE_VAR);

        c.aload(STATE_CURRENT_BYTES_VAR);
        c.arraylength();
        c.iload(STATE_POS_VAR);
        BranchEnd correctLength = c.ifIcmpeq();

        c.newInstruction(HTTP_STRING_CLASS);
        c.dup();
        c.aload(STATE_CURRENT_BYTES_VAR);
        c.iconst(0);
        c.iload(STATE_POS_VAR);
        c.invokespecial(HTTP_STRING_CLASS, "<init>", "([BII)V");
        stateMachine.handleOtherToken(c);
        //TODO: exit if it returns null
        //decrease the available bytes
        c.pop();
        tokenDone(c, returnCompleteCode, stateMachine);

        c.branchEnd(correctLength);

        c.aload(STATE_CURRENT_VAR);
        stateMachine.handleStateMachineMatchedToken(c);
        //TODO: exit if it returns null
        c.pop();
        tokenDone(c, returnCompleteCode, stateMachine);


        //nostate
        c.branchEnd(noState.get());
        c.branchEnd(prefixToNoState);
        CodeLocation noStateLoop = c.mark();

        handleReturnIfNoMoreBytes(c, returnIncompleteCode);
        //load 2 copies of the current byte into the stack
        c.aload(BYTE_BUFFER_VAR);
        c.invokevirtual(ByteBuffer.class.getName(), "get", "()B");
        c.dup();

        final Set<BranchEnd> nostateHandleSpace = new LinkedHashSet<>();
        final Set<BranchEnd> badNostateHandleSpace = new LinkedHashSet<>();
        if (stateMachine.isHeader()) {
            c.iconst(':');
            nostateHandleSpace.add(c.ifIcmpeq());
            c.dup();
            c.iconst(' ');
            nostateHandleSpace.add(c.ifIcmpeq());
            c.dup();
            c.iconst('\r');
            nostateHandleSpace.add(c.ifIcmpeq());
            c.dup();
            c.iconst('\n');
            nostateHandleSpace.add(c.ifIcmpeq());
        } else if(!expectNewline) {
            c.iconst(' ');
            nostateHandleSpace.add(c.ifIcmpeq());
            c.dup();
            c.iconst('\r');
            badNostateHandleSpace.add(c.ifIcmpeq());
            c.dup();
            c.iconst('\n');
            badNostateHandleSpace.add(c.ifIcmpeq());
        } else {
            c.iconst('\r');
            nostateHandleSpace.add(c.ifIcmpeq());
            c.dup();
            c.iconst('\n');
            nostateHandleSpace.add(c.ifIcmpeq());
        }
        c.aload(STATE_STRING_BUILDER_VAR);
        c.swap();
        c.invokevirtual(StringBuilder.class.getName(), "append", "(C)Ljava/lang/StringBuilder;");
        c.pop();
        c.aload(BYTE_BUFFER_VAR);
        c.invokevirtual(ByteBuffer.class.getName(), "hasRemaining", "()Z");
        c.ifne(noStateLoop); //go back to the start if we have not run out of bytes

        //we have run out of bytes, so we need to write back the current state
        c.aload(PARSE_STATE_VAR);
        c.iload(CURRENT_STATE_VAR);
        c.putfield(parseStateClass, "parseState", "I");
        c.iconst(0);
        c.returnInstruction();

        if(!badNostateHandleSpace.isEmpty()) {
            //handle the space case
            for (BranchEnd b : badNostateHandleSpace) {
                c.branchEnd(b);
            }
            c.newInstruction(BAD_REQUEST_EXCEPTION);
            c.dup();
            c.invokespecial(BAD_REQUEST_EXCEPTION, "<init>", "()V");
            c.athrow();
        }
        for (BranchEnd b : nostateHandleSpace) {
            c.branchEnd(b);
        }
        c.aload(STATE_STRING_BUILDER_VAR);
        c.invokevirtual(StringBuilder.class.getName(), "toString", "()Ljava/lang/String;");

        c.newInstruction(HTTP_STRING_CLASS);
        c.dupX1();
        c.swap();
        c.invokespecial(HTTP_STRING_CLASS, "<init>", "(Ljava/lang/String;)V");
        stateMachine.handleOtherToken(c);
        //TODO: exit if it returns null
        tokenDone(c, returnCompleteCode, stateMachine);

        c.branchEnd(optimizationEnd);
        c.pop();
        c.iconst(0);
        c.istore(STATE_POS_VAR);
        c.aconstNull();
        c.astore(STATE_CURRENT_VAR);
        c.aconstNull();
        c.astore(STATE_CURRENT_BYTES_VAR);

        c.branchEnd(ends.get(initial).get());
        invokeState(className, file, c, initial, initial, noStateLoop, prefixLoop, returnIncompleteCode, returnCompleteCode, stateMachine, expectNewline);
        for (final State s : allStates) {
            if (s.stateno >= 0) {
                c.branchEnd(ends.get(s).get());
                invokeState(className, file, c, s, initial, noStateLoop, prefixLoop, returnIncompleteCode, returnCompleteCode, stateMachine, expectNewline);
            }
        }

    }

    private void setupLocalVariables(final CodeAttribute c) {
        c.setupFrame(DescriptorUtils.makeDescriptor(existingClassName + CLASS_NAME_SUFFIX),
                DescriptorUtils.makeDescriptor(ByteBuffer.class),
                parseStateDescriptor,
                httpExchangeDescriptor,
                "I",
                "I",
                HTTP_STRING_DESCRIPTOR,
                DescriptorUtils.makeDescriptor(StringBuilder.class),
                "[B");
    }

    private void handleReturnIfNoMoreBytes(final CodeAttribute c, final CodeLocation returnCode) {
        c.aload(BYTE_BUFFER_VAR);
        c.invokevirtual(ByteBuffer.class.getName(), "hasRemaining", "()Z");
        c.ifEq(returnCode); //go back to the start if we have not run out of bytes
    }

    private void tokenDone(final CodeAttribute c, final CodeLocation returnCode, final CustomStateMachine stateMachine) {
        stateMachine.updateParseState(c);
        c.gotoInstruction(returnCode);
    }

    private void invokeState(final String className, final ClassFile file, final CodeAttribute c, final State currentState, final State initialState, final CodeLocation noStateStart, final CodeLocation prefixStart, final CodeLocation returnIncompleteCode, final CodeLocation returnCompleteCode, final CustomStateMachine stateMachine, boolean expectNewline) {
        currentState.mark(c);

        BranchEnd parseDone = null;

        if (currentState == initialState) {
            //if this is the initial state there is a possibility that we need to deal with a left over character first
            //we need to see if we start with a left over character
            c.aload(PARSE_STATE_VAR);
            c.getfield(parseStateClass, "leftOver", "B");
            c.dup();
            final BranchEnd end = c.ifne();
            c.pop();
            //load 2 copies of the current byte into the stack
            handleReturnIfNoMoreBytes(c, returnIncompleteCode);
            c.aload(BYTE_BUFFER_VAR);
            c.invokevirtual(ByteBuffer.class.getName(), "get", "()B");
            BranchEnd cont = c.gotoInstruction();
            c.branchEnd(end);
            c.aload(PARSE_STATE_VAR);
            c.iconst(0);
            c.putfield(parseStateClass, "leftOver", "B");

            c.branchEnd(cont);

        } else {
            handleReturnIfNoMoreBytes(c, returnIncompleteCode);
            //load 2 copies of the current byte into the stack
            c.aload(BYTE_BUFFER_VAR);
            c.invokevirtual(ByteBuffer.class.getName(), "get", "()B");
        }

        c.dup();
        final Set<BranchEnd> tokenEnds = new LinkedHashSet<>();
        final Set<BranchEnd> badTokenEnds = new LinkedHashSet<>();
        final Map<State, BranchEnd> ends = new IdentityHashMap<>();
        for (State state : currentState.next.values()) {
            c.iconst(state.value);
            ends.put(state, c.ifIcmpeq());
            c.dup();
        }
        if (stateMachine.isHeader()) {
            c.iconst(':');
            tokenEnds.add(c.ifIcmpeq());
            c.dup();
            c.iconst('\r');
            tokenEnds.add(c.ifIcmpeq());
            c.dup();
            c.iconst('\n');
            tokenEnds.add(c.ifIcmpeq());
            c.dup();
            c.iconst(' ');
            tokenEnds.add(c.ifIcmpeq());
        }else if (expectNewline) {
            c.iconst('\r');
            tokenEnds.add(c.ifIcmpeq());
            c.dup();
            c.iconst('\n');
            tokenEnds.add(c.ifIcmpeq());
        } else {
            c.iconst(' ');
            tokenEnds.add(c.ifIcmpeq());
            c.dup();
            c.iconst('\r');
            badTokenEnds.add(c.ifIcmpeq());
            c.dup();
            c.iconst('\n');
            badTokenEnds.add(c.ifIcmpeq());
        }


        c.iconst(NO_STATE);
        c.istore(CURRENT_STATE_VAR);

        //create the string builder
        c.aload(STATE_STRING_BUILDER_VAR);
        c.ldc(currentState.soFar);
        c.invokevirtual(StringBuilder.class.getName(), "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        c.swap();
        c.invokevirtual(StringBuilder.class.getName(), "append", "(C)Ljava/lang/StringBuilder;");
        c.pop();

        c.gotoInstruction(noStateStart);

        if(!badTokenEnds.isEmpty()) {
            //handle the space case
            for (BranchEnd b : badTokenEnds) {
                c.branchEnd(b);
            }
            c.newInstruction(BAD_REQUEST_EXCEPTION);
            c.dup();
            c.invokespecial(BAD_REQUEST_EXCEPTION, "<init>", "()V");
            c.athrow();
        }
        //now we write out tokenEnd
        for (BranchEnd tokenEnd : tokenEnds) {
            c.branchEnd(tokenEnd);
        }

        if (!currentState.soFar.isEmpty()) {
            c.getstatic(file.getName(), currentState.httpStringFieldName, HTTP_STRING_DESCRIPTOR);
            stateMachine.handleStateMachineMatchedToken(c);
            //TODO: exit if it returns null
            tokenDone(c, returnCompleteCode, stateMachine);
        } else {
            if (stateMachine.initialNewlineMeansRequestDone()) {
                c.iconst('\n');
                parseDone = c.ifIcmpeq();
            } else {
                c.pop();
            }
            setupLocalVariables(c);
            handleReturnIfNoMoreBytes(c, returnIncompleteCode);
            initialState.jumpTo(c);
        }

        for (Map.Entry<State, BranchEnd> e : ends.entrySet()) {
            c.branchEnd(e.getValue());
            c.pop();
            final State state = e.getKey();
            if (state.stateno < 0) {
                //prefix match
                c.iconst(state.stateno);
                c.istore(CURRENT_STATE_VAR);
                c.getstatic(className, state.httpStringFieldName, HTTP_STRING_DESCRIPTOR);
                c.astore(STATE_CURRENT_VAR);
                c.getstatic(className, state.fieldName, "[B");
                c.astore(STATE_CURRENT_BYTES_VAR);
                c.iconst(state.soFar.length());
                c.istore(STATE_POS_VAR);
                c.gotoInstruction(prefixStart);
            } else {

                c.iconst(state.stateno);
                c.istore(CURRENT_STATE_VAR);
                state.jumpTo(c);
            }
        }
        if (parseDone != null) {
            c.branchEnd(parseDone);

            c.aload(PARSE_STATE_VAR);
            c.invokevirtual(parseStateClass, "parseComplete", "()V");
            c.iconst(0);
            c.returnInstruction();
        }
    }

    /**
     * Throws an exception when an invalid state is hit in a tableswitch
     */
    private static void stateNotFound(final CodeAttribute c, final TableSwitchBuilder builder) {
        c.branchEnd(builder.getDefaultBranchEnd().get());
        c.newInstruction(RuntimeException.class);
        c.dup();
        c.ldc("Invalid character");
        c.invokespecial(RuntimeException.class.getName(), "<init>", "(Ljava/lang/String;)V");
        c.athrow();
    }

    private static void addStates(final State initial, final String value, final List<State> allStates) {
        addStates(initial, value, 0, allStates);
    }

    private static void addStates(final State current, final String value, final int i, final List<State> allStates) {
        if (i == value.length()) {
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        final byte currentByte = bytes[i];
        State newState = current.next.get(currentByte);
        if (newState == null) {
            current.next.put(currentByte, newState = new State(currentByte, value.substring(0, i + 1)));
            allStates.add(newState);
        }
        addStates(newState, value, i + 1, allStates);
    }

    private static class State implements Comparable<State> {

        Integer stateno;
        String terminalState;
        String fieldName;
        String httpStringFieldName;
        final byte value;
        final String soFar;
        final Map<Byte, State> next = new LinkedHashMap<>();
        private final Set<BranchEnd> branchEnds = new LinkedHashSet<>();
        private CodeLocation location;

        private State(final byte value, final String soFar) {
            this.value = value;
            this.soFar = soFar;
        }

        @Override
        public int hashCode() {
            return stateno.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof State)) {
              return false;
            }
            State other = (State) obj;
            return stateno.equals(other.stateno);
        }

        @Override
        public int compareTo(final State o) {
            return stateno.compareTo(o.stateno);
        }

        void mark(final CodeAttribute ca) {
            location = ca.mark();
            for (BranchEnd br : branchEnds) {
                ca.branchEnd(br);
            }
        }

        void jumpTo(final CodeAttribute ca) {
            if (location == null) {
                branchEnds.add(ca.gotoInstruction());
            } else {
                ca.gotoInstruction(location);
            }
        }

        void ifne(final CodeAttribute ca) {
            if (location == null) {
                branchEnds.add(ca.ifne());
            } else {
                ca.ifne(location);
            }
        }
    }

    /**
     * A class that separates out the different behaviour of the three state machines (VERB, VERSION and HEADER)
     */
    public interface CustomStateMachine {

        boolean isHeader();

        void handleStateMachineMatchedToken(CodeAttribute c);

        void handleOtherToken(CodeAttribute c);

        void updateParseState(CodeAttribute c);

        boolean initialNewlineMeansRequestDone();
    }


}
