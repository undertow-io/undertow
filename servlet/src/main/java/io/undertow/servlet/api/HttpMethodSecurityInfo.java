package io.undertow.servlet.api;

import javax.servlet.annotation.ServletSecurity;

/**
 * @author Stuart Douglas
 */
public class HttpMethodSecurityInfo extends SecurityInfo implements Cloneable {

    private volatile ServletSecurity.EmptyRoleSemantic emptyRoleSemantic;
    private volatile String method;

    public ServletSecurity.EmptyRoleSemantic getEmptyRoleSemantic() {
        return emptyRoleSemantic;
    }

    public SecurityInfo setEmptyRoleSemantic(final ServletSecurity.EmptyRoleSemantic emptyRoleSemantic) {
        this.emptyRoleSemantic = emptyRoleSemantic;
        return this;
    }
    public String getMethod() {
        return method;
    }

    public HttpMethodSecurityInfo setMethod(final String method) {
        this.method = method;
        return this;
    }

    @Override
    protected SecurityInfo createInstance() {
        return new HttpMethodSecurityInfo();
    }

    @Override
    public HttpMethodSecurityInfo clone() {
        HttpMethodSecurityInfo info = (HttpMethodSecurityInfo) super.clone();
        info.emptyRoleSemantic = emptyRoleSemantic;
        info.method = method;
        return info;
    }
}
