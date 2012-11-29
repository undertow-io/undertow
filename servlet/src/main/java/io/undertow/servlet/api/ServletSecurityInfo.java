package io.undertow.servlet.api;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.annotation.ServletSecurity;

/**
 * @author Stuart Douglas
 */
public class ServletSecurityInfo extends SecurityInfo<ServletSecurityInfo> implements Cloneable {

    private volatile ServletSecurity.EmptyRoleSemantic emptyRoleSemantic = ServletSecurity.EmptyRoleSemantic.DENY;
    private final List<HttpMethodSecurityInfo> httpMethodSecurityInfo = new ArrayList<HttpMethodSecurityInfo>();

    public ServletSecurity.EmptyRoleSemantic getEmptyRoleSemantic() {
        return emptyRoleSemantic;
    }

    public SecurityInfo setEmptyRoleSemantic(final ServletSecurity.EmptyRoleSemantic emptyRoleSemantic) {
        this.emptyRoleSemantic = emptyRoleSemantic;
        return this;
    }

    @Override
    protected ServletSecurityInfo createInstance() {
        return new ServletSecurityInfo();
    }

    @Override
    public ServletSecurityInfo clone() {
        ServletSecurityInfo info = super.clone();
        info.emptyRoleSemantic = emptyRoleSemantic;
        for(HttpMethodSecurityInfo method : httpMethodSecurityInfo) {
            info.httpMethodSecurityInfo.add(method.clone());
        }
        return info;
    }
}
