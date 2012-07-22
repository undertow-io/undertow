/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tmp.texugo.annotationprocessor;

import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.classfilewriter.AccessFlag;
import org.jboss.classfilewriter.ClassFile;
import org.jboss.classfilewriter.ClassMethod;
import org.jboss.classfilewriter.code.BranchEnd;
import org.jboss.classfilewriter.code.CodeAttribute;
import org.jboss.classfilewriter.code.CodeLocation;
import org.jboss.classfilewriter.code.LookupSwitchBuilder;
import org.jboss.classfilewriter.code.TableSwitchBuilder;
import org.jboss.classfilewriter.util.DescriptorUtils;

/**
 * @author Stuart Douglas
 */
public class ParserGenerator {

    //class names
    public static final String PARSE_STATE_CLASS = "tmp.texugo.server.httpparser.ParseState";
    public static final String PARSE_STATE_DESCRIPTOR = DescriptorUtils.makeDescriptor(PARSE_STATE_CLASS);
    public static final String HTTP_EXCHANGE_BUILDER_CLASS = "tmp.texugo.server.httpparser.HttpExchangeBuilder";
    public static final String HTTP_EXCHANGE_BUILDER_DESCRIPTOR = DescriptorUtils.makeDescriptor(HTTP_EXCHANGE_BUILDER_CLASS);


    //state machine states
    public static final int NO_STATE = -1;
    public static final int PREFIX_MATCH = -2;

    //parsing states
    public static final int VERB = 0;
    public static final int PATH = 1;
    public static final int VERSION = 2;
    public static final int HEADER = 3;
    public static final int HEADER_VALUE = 4;
    public static final int PARSE_COMPLETE = 5;


    private static final int BYTE_BUFFER_VAR = 1;
    private static final int BYTES_REMAINING_VAR = 2;
    private static final int PARSE_STATE_VAR = 3;
    private static final int HTTP_EXCHANGE_BUILDER = 4;
    private static final int CURRENT_STATE_VAR = 5;
    private static final int STATE_POS_VAR = 6;
    private static final int STATE_CURRENT_VAR = 7;
    private static final int STATE_STRING_BUILDER_VAR = 8;
    private static final int STATE_CURRENT_BYTES_VAR = 9;

    public static final String HANDLE_HTTP_VERB = "handleHttpVerbs";
    public static final String HANDLE_PATH = "handlePath";
    public static final String HANDLE_HTTP_VERSION = "handleHttpVersion";
    public static final String HANDLE_HEADER = "handleHeader";
    public static final String HANDLE_HEADER_VALUE = "handleHeaderValue";
    public static final String CLASS_NAME_SUFFIX = "$$generated";

    public static byte[] createTokenizer(final String existingClassName, final String[] httpVerbs, String[] httpVersions, String[] standardHeaders) {
        final String className = existingClassName + CLASS_NAME_SUFFIX;
        final ClassFile file = new ClassFile(className, existingClassName);

        final ClassMethod ctor = file.addMethod(AccessFlag.PUBLIC, "<init>", "V");
        ctor.getCodeAttribute().aload(0);
        ctor.getCodeAttribute().invokespecial(existingClassName, "<init>", "()V");
        ctor.getCodeAttribute().returnInstruction();


        final ClassMethod sctor = file.addMethod(AccessFlag.PUBLIC | AccessFlag.STATIC, "<clinit>", "V");
        final AtomicInteger fieldCounter = new AtomicInteger(1);

        createStateMachine(httpVerbs, className, file, sctor, fieldCounter, HANDLE_HTTP_VERB, new VerbStateMachine());
        createStateMachine(httpVersions, className, file, sctor, fieldCounter, HANDLE_HTTP_VERSION, new VersionStateMachine());
        createStateMachine(standardHeaders, className, file, sctor, fieldCounter, HANDLE_HEADER, new HeaderStateMachine());

        final ClassMethod handle = file.addMethod(Modifier.PUBLIC, "handle", "I", DescriptorUtils.makeDescriptor(ByteBuffer.class), "I", PARSE_STATE_DESCRIPTOR, HTTP_EXCHANGE_BUILDER_DESCRIPTOR);
        createHandleBody(className, handle);


        sctor.getCodeAttribute().returnInstruction();
        return file.toBytecode();
    }

    private static void createHandleBody(final String className, final ClassMethod handle) {
        final CodeAttribute c = handle.getCodeAttribute();
        c.aload(PARSE_STATE_VAR);
        c.getfield(PARSE_STATE_CLASS, "state", "I");
        final Set<BranchEnd> returnSet = new HashSet<BranchEnd>();
        final TableSwitchBuilder builder = new TableSwitchBuilder(0, 4);
        final AtomicReference<BranchEnd> method = builder.add();
        final AtomicReference<BranchEnd> path = builder.add();
        final AtomicReference<BranchEnd> http = builder.add();
        final AtomicReference<BranchEnd> header = builder.add();
        final AtomicReference<BranchEnd> headerValue = builder.add();
        c.tableswitch(builder);
        c.branchEnd(method.get());
        c.aload(0);
        c.loadMethodParameters();
        c.invokespecial(className, HANDLE_HTTP_VERB, "I", new String[]{DescriptorUtils.makeDescriptor(ByteBuffer.class), "I", PARSE_STATE_DESCRIPTOR, HTTP_EXCHANGE_BUILDER_DESCRIPTOR});
        c.dup();
        c.istore(BYTES_REMAINING_VAR);
        returnSet.add(c.ifeq());
        //we just fall through to the path
        //we rely on the individual parsing methods to update the state as appropriate


        c.branchEnd(path.get());
        c.aload(0);
        c.loadMethodParameters();
        c.invokespecial(className, HANDLE_PATH, "I", new String[]{DescriptorUtils.makeDescriptor(ByteBuffer.class), "I", PARSE_STATE_DESCRIPTOR, HTTP_EXCHANGE_BUILDER_DESCRIPTOR});
        c.dup();
        c.istore(BYTES_REMAINING_VAR);
        returnSet.add(c.ifeq());


        c.branchEnd(http.get());
        c.aload(0);
        c.loadMethodParameters();
        c.invokespecial(className, HANDLE_HTTP_VERSION, "I", new String[]{DescriptorUtils.makeDescriptor(ByteBuffer.class), "I", PARSE_STATE_DESCRIPTOR, HTTP_EXCHANGE_BUILDER_DESCRIPTOR});
        c.dup();
        c.istore(BYTES_REMAINING_VAR);
        returnSet.add(c.ifeq());


        c.branchEnd(header.get());
        CodeLocation headerStart = c.mark();
        c.aload(0);
        c.loadMethodParameters();
        c.invokespecial(className, HANDLE_HEADER, "I", new String[]{DescriptorUtils.makeDescriptor(ByteBuffer.class), "I", PARSE_STATE_DESCRIPTOR, HTTP_EXCHANGE_BUILDER_DESCRIPTOR});
        c.dup();
        c.istore(BYTES_REMAINING_VAR);
        returnSet.add(c.ifeq());

        //it is possible that the header did not have a value
        c.aload(PARSE_STATE_VAR);
        c.getfield(PARSE_STATE_CLASS, "state", "I");
        c.iconst(PARSE_COMPLETE);
        returnSet.add(c.ifIcmpeq());

        c.branchEnd(headerValue.get());
        c.aload(0);
        c.loadMethodParameters();
        c.invokespecial(className, HANDLE_HEADER_VALUE, "I", new String[]{DescriptorUtils.makeDescriptor(ByteBuffer.class), "I", PARSE_STATE_DESCRIPTOR, HTTP_EXCHANGE_BUILDER_DESCRIPTOR});
        c.dup();
        c.istore(BYTES_REMAINING_VAR);
        returnSet.add(c.ifeq());

        c.aload(PARSE_STATE_VAR);
        c.getfield(PARSE_STATE_CLASS, "state", "I");
        c.iconst(PARSE_COMPLETE);
        returnSet.add(c.ifIcmpeq());

        c.gotoInstruction(headerStart);

        //all we need to do is return
        for (final BranchEnd b : returnSet) {
            c.branchEnd(b);
        }
        c.iload(BYTES_REMAINING_VAR);
        c.returnInstruction();

        stateNotFound(c, builder);

    }

    private static void createStateMachine(final String[] httpVerbs, final String className, final ClassFile file, final ClassMethod sctor, final AtomicInteger fieldCounter, final String methodName, final CustomStateMachine stateMachine) {
        //list of all states except the initial
        final List<State> allStates = new ArrayList<State>();
        final State initial = new State((byte) 0, "");
        for (String value : httpVerbs) {
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

        final ClassMethod handle = file.addMethod(Modifier.PRIVATE, methodName, "I", DescriptorUtils.makeDescriptor(ByteBuffer.class), "I", PARSE_STATE_DESCRIPTOR, HTTP_EXCHANGE_BUILDER_DESCRIPTOR);
        writeStateMachine(className, handle.getCodeAttribute(), initial, allStates, noStates, stateMachine);
    }

    private static void createStateField(final State state, final ClassFile file, final CodeAttribute sc) {
        if (state.fieldName != null) {
            file.addField(AccessFlag.STATIC | AccessFlag.FINAL | AccessFlag.PRIVATE, state.fieldName, "[B");
            sc.ldc(state.terminalState);
            sc.ldc("ISO-8859-1");
            sc.invokevirtual(String.class.getName(), "getBytes", "(Ljava/lang/String;)[B");
            sc.putstatic(file.getName(), state.fieldName, "[B");
        }
    }


    private static void setupStateNo(final State state, final AtomicInteger stateCounter, final AtomicInteger fieldCounter) {
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
    }

    private static void writeStateMachine(final String className, final CodeAttribute c, final State initial, final List<State> allStates, int noStates, final CustomStateMachine stateMachine) {

        final List<State> states = new ArrayList<State>();
        states.add(initial);
        states.addAll(allStates);
        Collections.sort(states);

        //store the current state in a local variable
        c.aload(PARSE_STATE_VAR);
        c.dup();
        c.dup();
        c.dup();
        c.dup();
        c.getfield(PARSE_STATE_CLASS, "parseState", "I");
        c.istore(CURRENT_STATE_VAR);
        c.getfield(PARSE_STATE_CLASS, "pos", "I");
        c.istore(STATE_POS_VAR);
        c.getfield(PARSE_STATE_CLASS, "current", "Ljava/lang/String;");
        c.astore(STATE_CURRENT_VAR);
        c.getfield(PARSE_STATE_CLASS, "currentBytes", "[B");
        c.astore(STATE_CURRENT_BYTES_VAR);
        c.getfield(PARSE_STATE_CLASS, "stringBuilder", DescriptorUtils.makeDescriptor(StringBuilder.class));
        c.astore(STATE_STRING_BUILDER_VAR);


        c.iload(BYTES_REMAINING_VAR);
        final BranchEnd nonZero = c.ifne();
        //we have run out of bytes, return 0
        c.iconst(0);
        c.returnInstruction();

        c.branchEnd(nonZero);

        //load the current state
        c.iload(CURRENT_STATE_VAR);
        //switch on the current state
        TableSwitchBuilder builder = new TableSwitchBuilder(-BYTES_REMAINING_VAR, noStates);
        final IdentityHashMap<State, AtomicReference<BranchEnd>> ends = new IdentityHashMap<State, AtomicReference<BranchEnd>>();
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
        c.putfield(PARSE_STATE_CLASS, "pos", "I");
        c.aload(STATE_CURRENT_VAR);
        c.putfield(PARSE_STATE_CLASS, "current", DescriptorUtils.makeDescriptor(String.class));
        c.aload(STATE_CURRENT_BYTES_VAR);
        c.putfield(PARSE_STATE_CLASS, "currentBytes", "[B");
        c.aload(STATE_STRING_BUILDER_VAR);
        c.putfield(PARSE_STATE_CLASS, "stringBuilder", DescriptorUtils.makeDescriptor(StringBuilder.class));
        c.iload(CURRENT_STATE_VAR);
        c.putfield(PARSE_STATE_CLASS, "parseState", "I");
        c.iload(BYTES_REMAINING_VAR);
        c.returnInstruction();
        setupLocalVariables(c);
        final CodeLocation returnCompleteCode = c.mark();
        c.aload(PARSE_STATE_VAR);
        c.dup();
        c.dup();
        c.dup();
        c.dup();

        c.iconst(0);
        c.putfield(PARSE_STATE_CLASS, "pos", "I");
        c.aconstNull();
        c.putfield(PARSE_STATE_CLASS, "current", DescriptorUtils.makeDescriptor(String.class));
        c.aconstNull();
        c.putfield(PARSE_STATE_CLASS, "currentBytes", "[B");
        c.aconstNull();
        c.putfield(PARSE_STATE_CLASS, "stringBuilder", DescriptorUtils.makeDescriptor(StringBuilder.class));
        c.iconst(0);
        c.putfield(PARSE_STATE_CLASS, "parseState", "I");
        c.iload(BYTES_REMAINING_VAR);
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
        c.iinc(BYTES_REMAINING_VAR, -1);
        final Set<BranchEnd> prefixHandleSpace = new HashSet<BranchEnd>();
        if (stateMachine.isHeader()) {
            c.iconst(':');
            prefixHandleSpace.add(c.ifIcmpeq());
            c.dup();
        }
        c.iconst(' ');
        prefixHandleSpace.add(c.ifIcmpeq());
        c.dup();
        c.iconst('\t');
        prefixHandleSpace.add(c.ifIcmpeq());
        c.dup();
        c.iconst('\r');
        prefixHandleSpace.add(c.ifIcmpeq());
        c.dup();
        c.iconst('\n');
        prefixHandleSpace.add(c.ifIcmpeq());

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
        c.newInstruction(StringBuilder.class);
        c.dup();
        c.aload(STATE_CURRENT_VAR);
        c.iconst(0);
        c.iload(STATE_POS_VAR);
        c.invokevirtual(String.class.getName(), "substring", "(II)Ljava/lang/String;");
        c.invokespecial(StringBuilder.class.getName(), "<init>", "(Ljava/lang/String;)V");
        c.swap();

        c.invokevirtual(StringBuilder.class.getName(), "append", "(C)Ljava/lang/StringBuilder;");
        c.astore(STATE_STRING_BUILDER_VAR);
        c.pop();
        BranchEnd prefixToNoState = c.gotoInstruction();

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

        c.aload(STATE_CURRENT_VAR);
        c.iconst(0);
        c.iload(STATE_POS_VAR);
        c.invokevirtual(String.class.getName(), "substring", "(II)Ljava/lang/String;");
        stateMachine.handleOtherToken(c);
        //TODO: exit if it returns null
        //decrease the available bytes
        c.pop2();
        tokenDone(c, returnCompleteCode, stateMachine);

        c.branchEnd(correctLength);

        c.aload(STATE_CURRENT_VAR);
        stateMachine.handleStateMachineMatchedToken(c);
        //TODO: exit if it returns null
        c.pop2();
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
        c.iinc(BYTES_REMAINING_VAR, -1);

        final Set<BranchEnd> nostateHandleSpace = new HashSet<BranchEnd>();
        if (stateMachine.isHeader()) {
            c.iconst(':');
            nostateHandleSpace.add(c.ifIcmpeq());
            c.dup();
        }
        c.iconst(' ');
        nostateHandleSpace.add(c.ifIcmpeq());
        c.dup();
        c.iconst('\t');
        nostateHandleSpace.add(c.ifIcmpeq());
        c.dup();
        c.iconst('\r');
        nostateHandleSpace.add(c.ifIcmpeq());
        c.dup();
        c.iconst('\n');
        nostateHandleSpace.add(c.ifIcmpeq());
        c.aload(STATE_STRING_BUILDER_VAR);
        c.swap();
        c.invokevirtual(StringBuilder.class.getName(), "append", "(C)Ljava/lang/StringBuilder;");
        c.pop();
        c.iload(BYTES_REMAINING_VAR);
        c.ifne(noStateLoop); //go back to the start if we have not run out of bytes

        //we have run out of bytes, so we need to write back the current state
        c.aload(PARSE_STATE_VAR);
        c.dup();
        c.aload(STATE_STRING_BUILDER_VAR);
        c.putfield(PARSE_STATE_CLASS, "stringBuilder", DescriptorUtils.makeDescriptor(StringBuilder.class));
        c.iload(CURRENT_STATE_VAR);
        c.putfield(PARSE_STATE_CLASS, "parseState", "I");
        c.iconst(0);
        c.returnInstruction();
        for (BranchEnd b : nostateHandleSpace) {
            c.branchEnd(b);
        }
        c.aload(STATE_STRING_BUILDER_VAR);
        c.invokevirtual(StringBuilder.class.getName(), "toString", "()Ljava/lang/String;");
        c.aconstNull();
        c.astore(STATE_STRING_BUILDER_VAR);
        stateMachine.handleOtherToken(c);
        //TODO: exit if it returns null
        c.pop();
        tokenDone(c, returnCompleteCode, stateMachine);


        invokeState(className, c, ends.get(initial).get(), initial, initial, noStateLoop, prefixLoop, returnIncompleteCode, returnCompleteCode, stateMachine);
        for (final State s : allStates) {
            if (s.stateno >= 0) {
                invokeState(className, c, ends.get(s).get(), s, initial, noStateLoop, prefixLoop, returnIncompleteCode, returnCompleteCode, stateMachine);
            }
        }
    }

    private static void setupLocalVariables(final CodeAttribute c) {
        c.setupFrame(DescriptorUtils.makeDescriptor("fakeclass"),
                "[B",
                "I",
                PARSE_STATE_DESCRIPTOR,
                HTTP_EXCHANGE_BUILDER_DESCRIPTOR,
                "I",
                "I",
                DescriptorUtils.makeDescriptor(String.class),
                DescriptorUtils.makeDescriptor(StringBuilder.class),
                "[B");
    }

    private static void handleReturnIfNoMoreBytes(final CodeAttribute c, final CodeLocation returnCode) {
        c.iload(BYTES_REMAINING_VAR);
        c.ifEq(returnCode); //go back to the start if we have not run out of bytes
    }

    private static void tokenDone(final CodeAttribute c, final CodeLocation returnCode, final CustomStateMachine stateMachine) {
        stateMachine.updateParseState(c);
        c.gotoInstruction(returnCode);
    }

    private static void invokeState(final String className, final CodeAttribute c, BranchEnd methodState, final State currentState, final State initialState, final CodeLocation noStateStart, final CodeLocation prefixStart, final CodeLocation returnIncompleteCode, final CodeLocation returnCompleteCode, final CustomStateMachine stateMachine) {
        c.branchEnd(methodState);
        currentState.mark(c);

        if (currentState == initialState) {
            //if this is the initial state there is a possibility that we need to deal with a left over character first
            //we need to see if we start with a left over character
            c.aload(PARSE_STATE_VAR);
            c.getfield(PARSE_STATE_CLASS, "leftOver", "B");
            c.dup();
            final BranchEnd end = c.ifne();
            c.pop();
            //load 2 copies of the current byte into the stack
            handleReturnIfNoMoreBytes(c, returnIncompleteCode);
            c.aload(BYTE_BUFFER_VAR);
            c.invokevirtual(ByteBuffer.class.getName(), "get", "()B");
            c.iinc(BYTES_REMAINING_VAR, -1);
            BranchEnd cont = c.gotoInstruction();
            c.branchEnd(end);
            c.aload(PARSE_STATE_VAR);
            c.iconst(0);
            c.putfield(PARSE_STATE_CLASS, "leftOver", "B");

            c.branchEnd(cont);

        } else {
            handleReturnIfNoMoreBytes(c, returnIncompleteCode);
            //load 2 copies of the current byte into the stack
            c.aload(BYTE_BUFFER_VAR);
            c.invokevirtual(ByteBuffer.class.getName(), "get", "()B");
            c.iinc(BYTES_REMAINING_VAR, -1);
        }

        c.dup();
        final Set<AtomicReference<BranchEnd>> tokenEnds = new HashSet<AtomicReference<BranchEnd>>();
        final Map<State, AtomicReference<BranchEnd>> ends = new IdentityHashMap<State, AtomicReference<BranchEnd>>();
        if (currentState.next.size() > 6) {
            final LookupSwitchBuilder s = new LookupSwitchBuilder();
            if (stateMachine.isHeader()) {
                tokenEnds.add(s.add((byte) ':'));
            }
            tokenEnds.add(s.add((byte) ' '));
            tokenEnds.add(s.add((byte) '\t'));
            tokenEnds.add(s.add((byte) '\r'));
            tokenEnds.add(s.add((byte) '\n'));
            for (final State state : currentState.next.values()) {
                ends.put(state, s.add(state.value));
            }
            c.lookupswitch(s);
            final BranchEnd defaultSetup = s.getDefaultBranchEnd().get();
            c.branchEnd(defaultSetup);
        } else {
            for (State state : currentState.next.values()) {
                c.iconst(state.value);
                ends.put(state, new AtomicReference<BranchEnd>(c.ifIcmpeq()));
                c.dup();
            }
            if (stateMachine.isHeader()) {
                c.iconst(':');
                tokenEnds.add(new AtomicReference<BranchEnd>(c.ifIcmpeq()));
                c.dup();
            }
            c.iconst(' ');
            tokenEnds.add(new AtomicReference<BranchEnd>(c.ifIcmpeq()));
            c.dup();
            c.iconst('\t');
            tokenEnds.add(new AtomicReference<BranchEnd>(c.ifIcmpeq()));
            c.dup();
            c.iconst('\r');
            tokenEnds.add(new AtomicReference<BranchEnd>(c.ifIcmpeq()));
            c.dup();
            c.iconst('\n');
            tokenEnds.add(new AtomicReference<BranchEnd>(c.ifIcmpeq()));

        }

        c.iconst(NO_STATE);
        c.istore(CURRENT_STATE_VAR);

        //create the string builder
        c.newInstruction(StringBuilder.class);
        c.dup();
        c.ldc(currentState.soFar);
        c.invokespecial(StringBuilder.class.getName(), "<init>", "(Ljava/lang/String;)V");
        c.swap();

        c.invokevirtual(StringBuilder.class.getName(), "append", "(C)Ljava/lang/StringBuilder;");

        c.astore(STATE_STRING_BUILDER_VAR);
        c.gotoInstruction(noStateStart);

        //now we write out tokenEnd
        for (AtomicReference<BranchEnd> tokenEnd : tokenEnds) {
            c.branchEnd(tokenEnd.get());
        }

        c.pop(); //opo off our extra byte, we don't need it
        if (!currentState.soFar.equals("")) {
            c.ldc(currentState.soFar);
            if (currentState.finalState) {
                stateMachine.handleStateMachineMatchedToken(c);
            } else {
                stateMachine.handleOtherToken(c);
            }
            //TODO: exit if it returns null
            tokenDone(c, returnCompleteCode, stateMachine);
        } else {
            setupLocalVariables(c);
            handleReturnIfNoMoreBytes(c, returnIncompleteCode);
        }
        initialState.jumpTo(c);

        for (Map.Entry<State, AtomicReference<BranchEnd>> e : ends.entrySet()) {
            c.branchEnd(e.getValue().get());
            c.pop();
            final State state = e.getKey();
            if (state.stateno < 0) {
                //prefix match
                c.iconst(state.stateno);
                c.istore(CURRENT_STATE_VAR);
                c.ldc(state.terminalState);
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
    }

    /**
     * Throws an exception when an invalid state is hit in a tableswitch
     */
    private static void stateNotFound(final CodeAttribute c, final TableSwitchBuilder builder) {
        c.branchEnd(builder.getDefaultBranchEnd().get());
        c.newInstruction(RuntimeException.class);
        c.dup();
        c.ldc("Could not find state");
        c.invokespecial(RuntimeException.class.getName(), "<init>", "(Ljava/lang/String;)V");
        c.athrow();
    }

    private static void addStates(final State initial, final String value, final List<State> allStates) {
        addStates(initial, value, 0, allStates);
    }

    private static void addStates(final State current, final String value, final int i, final List<State> allStates) {
        if (i == value.length()) {
            current.finalState = true;
            return;
        }
        byte[] bytes = value.getBytes();
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
        /**
         * If this state represents a possible final state
         */
        boolean finalState;
        final byte value;
        final String soFar;
        final Map<Byte, State> next = new HashMap<Byte, State>();
        private final Set<BranchEnd> branchEnds = new HashSet<BranchEnd>();
        private CodeLocation location;

        private State(final byte value, final String soFar) {
            this.value = value;
            this.soFar = soFar;
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
    private interface CustomStateMachine {

        boolean isHeader();

        void handleStateMachineMatchedToken(final CodeAttribute c);

        void handleOtherToken(final CodeAttribute c);

        void updateParseState(CodeAttribute c);
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
            c.putfield(PARSE_STATE_CLASS, "nextHeader", DescriptorUtils.makeDescriptor(String.class));
        }

        @Override
        public void handleStateMachineMatchedToken(final CodeAttribute c) {
            c.aload(PARSE_STATE_VAR);
            c.swap();
            c.putfield(PARSE_STATE_CLASS, "nextHeader", DescriptorUtils.makeDescriptor(String.class));
        }

        @Override
        public void updateParseState(final CodeAttribute c) {
            c.aload(PARSE_STATE_VAR);
            c.iconst(HEADER_VALUE);
            c.putfield(PARSE_STATE_CLASS, "state", "I");
        }
    }

    private static class VerbStateMachine implements CustomStateMachine {

        @Override
        public boolean isHeader() {
            return false;
        }

        @Override
        public void handleStateMachineMatchedToken(final CodeAttribute c) {
            handleOtherToken(c);
        }

        @Override
        public void handleOtherToken(final CodeAttribute c) {
            c.aload(HTTP_EXCHANGE_BUILDER);
            c.swap();
            c.putfield(HTTP_EXCHANGE_BUILDER_CLASS, "method", DescriptorUtils.makeDescriptor(String.class));
        }

        @Override
        public void updateParseState(final CodeAttribute c) {
            c.aload(PARSE_STATE_VAR);
            c.iconst(PATH);
            c.putfield(PARSE_STATE_CLASS, "state", "I");
        }
    }

    private static class VersionStateMachine implements CustomStateMachine {

        @Override
        public boolean isHeader() {
            return false;
        }

        @Override
        public void handleOtherToken(final CodeAttribute c) {
            c.aload(HTTP_EXCHANGE_BUILDER);
            c.swap();
            c.putfield(HTTP_EXCHANGE_BUILDER_CLASS, "protocol", DescriptorUtils.makeDescriptor(String.class));
        }

        @Override
        public void handleStateMachineMatchedToken(final CodeAttribute c) {
            handleOtherToken(c);
        }

        @Override
        public void updateParseState(final CodeAttribute c) {
            c.aload(PARSE_STATE_VAR);
            c.iconst(HEADER);
            c.putfield(PARSE_STATE_CLASS, "state", "I");
        }

    }
}
