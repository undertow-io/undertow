package io.undertow.security.idm;

import java.io.Serializable;

/**
 * Representation of an external credential. This basically represents a trusted
 * 3rd party, e.g. a front end server that has performed authentication.
 *
 * @author Stuart Douglas
 */
public class ExternalCredential implements Serializable, Credential {

    public static final ExternalCredential INSTANCE = new ExternalCredential();

    private ExternalCredential() {

    }

}
