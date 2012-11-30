package io.undertow.servlet.handlers;

import java.util.List;
import java.util.Set;

import javax.servlet.annotation.ServletSecurity;

import io.undertow.servlet.api.ServletInfo;
import io.undertow.util.AttachmentKey;

/**
 * @author Stuart Douglas
 */
public class ServletAttachments {

    public static final AttachmentKey<ServletInfo> CURRENT_SERVLET = AttachmentKey.create(ServletInfo.class);
    public static final AttachmentKey<ServletPathMatch> SERVLET_PATH_MATCH = AttachmentKey.create(ServletPathMatch.class);

    public static final AttachmentKey<List<Set<String>>> REQUIRED_ROLES = AttachmentKey.create(List.class);
    public static final AttachmentKey<ServletSecurity.TransportGuarantee> TRANSPORT_GUARANTEE_TYPE = AttachmentKey.create(ServletSecurity.TransportGuarantee.class);
}
