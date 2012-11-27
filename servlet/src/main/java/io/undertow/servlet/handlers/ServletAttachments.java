package io.undertow.servlet.handlers;

import io.undertow.servlet.api.ServletInfo;
import io.undertow.util.AttachmentKey;

/**
 * @author Stuart Douglas
 */
public class ServletAttachments {
    public static final AttachmentKey<ServletInfo> CURRENT_SERVLET = AttachmentKey.create(ServletInfo.class);
    public static final AttachmentKey<ServletPathMatch> SERVLET_PATH_MATCH = AttachmentKey.create(ServletPathMatch.class);
}
