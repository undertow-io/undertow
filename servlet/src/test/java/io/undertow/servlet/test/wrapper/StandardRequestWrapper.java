package io.undertow.servlet.test.wrapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/**
 * @author Stuart Douglas
 */
public class StandardRequestWrapper extends HttpServletRequestWrapper {
    /**
     * Constructs a request object wrapping the given request.
     *
     * @throws IllegalArgumentException
     *          if the request is null
     */
    public StandardRequestWrapper(final HttpServletRequest request) {
        super(request);
    }


}
