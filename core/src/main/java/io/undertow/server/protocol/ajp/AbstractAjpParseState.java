package io.undertow.server.protocol.ajp;

/**
 * Abstract AJP parse state. Stores state common to both request and response parsers
 *
 *
 * @author Stuart Douglas
 */
public class AbstractAjpParseState {

    /**
     * The length of the string being read
     */
    public int stringLength = -1;

    /**
     * The current string being read
     */
    public StringBuilder currentString;

    /**
     * when reading the first byte of an integer this stores the first value. It is set to -1 to signify that
     * the first byte has not been read yet.
     */
    public int currentIntegerPart = -1;

    boolean containsUrlCharacters = false;
    public int readHeaders = 0;

    public void reset() {
        stringLength = -1;
        currentString = null;
        currentIntegerPart = -1;
        readHeaders = 0;
    }
}
