package io.undertow.servlet.api;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Stuart Douglas
 */
public class ServletSecurityInfo extends SecurityInfo<ServletSecurityInfo> implements Cloneable {

    private final List<HttpMethodSecurityInfo> httpMethodSecurityInfo = new ArrayList<HttpMethodSecurityInfo>();

    @Override
    protected ServletSecurityInfo createInstance() {
        return new ServletSecurityInfo();
    }

    public ServletSecurityInfo addHttpMethodSecurityInfo(final HttpMethodSecurityInfo info) {
        httpMethodSecurityInfo.add(info);
        return this;
    }

    public List<HttpMethodSecurityInfo> getHttpMethodSecurityInfo() {
        return new ArrayList<HttpMethodSecurityInfo>(httpMethodSecurityInfo);
    }

    @Override
    public ServletSecurityInfo clone() {
        ServletSecurityInfo info = super.clone();
        for(HttpMethodSecurityInfo method : httpMethodSecurityInfo) {
            info.httpMethodSecurityInfo.add(method.clone());
        }
        return info;
    }
}
