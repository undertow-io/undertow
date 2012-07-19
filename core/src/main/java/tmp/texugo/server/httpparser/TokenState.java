/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package tmp.texugo.server.httpparser;

/**
 * The current state of the tokenizer state machine. This class is mutable and not thread safe.
 * <p/>
 * As the machine changes state this class is updated rather than allocating a new one each time.
 *
 * fields are not private to allow for efficient putfield / getfield access
 *
 * @author Stuart Douglas
 */
public class TokenState {

    //parsing states
    public static final int VERB = 0;
    public static final int PATH = 1;
    public static final int VERSION = 2;
    public static final int HEADER = 3;
    public static final int HEADER_VALUE = 4;
    public static final int PARSE_COMPLETE = 5;

    /**
     * The actual state of request parsing
     */
    int state;

    /**
     * The current state in the tokenizer state machine.
     */
    int parseState;

    /**
     * If this state is a prefix or terminal match state this is set to the string
     * that is a candiate to be matched
     */
    String current;

    /**
     * The bytes version of {@link #current}
     */
    byte[] currentBytes;

    /**
     * If this state is a prefix match state then this holds the current position in the string.
     */
    int pos;

    /**
     * If this is in {@link #NO_STATE} then this holds the current token that has been read so far.
     */
    StringBuilder stringBuilder;

    /**
     * This has different meanings depending on the current state.
     *
     * In state {@link #HEADER} it is a the first character of the header, that was read by
     * {@link #HEADER_VALUE} to see if this was a continuation.
     *
     * In state {@link #HEADER_VALUE} if represents the last character that was seen.
     */
    byte leftOver;

    public TokenState() {
        this.parseState = 0;
        this.current = null;
        this.pos = 0;
    }

    public boolean isComplete() {
        return state == PARSE_COMPLETE;
    }
}
