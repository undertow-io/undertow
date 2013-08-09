package io.undertow.client.ajp;

import io.undertow.util.AttachmentKey;

/**
 * Additional attachments that are specific to AJP client requests.
 *
 * @author Stuart Douglas
 */
public class AjpClientAttachments {

    public static final AttachmentKey<String> REMOTE_ADDRESS = AttachmentKey.create(String.class);
    public static final AttachmentKey<String> REMOTE_HOST = AttachmentKey.create(String.class);
    public static final AttachmentKey<String> SERVER_NAME = AttachmentKey.create(String.class);
    public static final AttachmentKey<Integer> SERVER_PORT = AttachmentKey.create(Integer.class);
    public static final AttachmentKey<Boolean> IS_SSL = AttachmentKey.create(Boolean.class);

}
