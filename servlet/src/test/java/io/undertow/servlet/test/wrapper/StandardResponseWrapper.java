package io.undertow.servlet.test.wrapper;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * @author Stuart Douglas
 */
public class StandardResponseWrapper extends HttpServletResponseWrapper {

    /**
     * Constructs a response adaptor wrapping the given response.
     *
     * @throws IllegalArgumentException if the response is null
     */
    public StandardResponseWrapper(final HttpServletResponse response) {
        super(response);
    }

}
