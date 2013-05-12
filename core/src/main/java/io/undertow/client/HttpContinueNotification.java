package io.undertow.client;

/**
 * Callback class that provides a notification of a HTTP 100 Continue response in the client.
 *
 * @author Stuart Douglas
 */
public interface HttpContinueNotification {

    void handleContinue(ContinueContext context);

    interface ContinueContext {
        void done();
    }

}
