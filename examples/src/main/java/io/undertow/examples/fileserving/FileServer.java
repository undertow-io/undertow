package io.undertow.examples.fileserving;

import io.undertow.Undertow;
import io.undertow.examples.UndertowExample;
import io.undertow.server.handlers.resource.FileResourceManager;

import java.io.File;

import static io.undertow.Handlers.resource;

/**
 * @author Stuart Douglas
 */
@UndertowExample("File Serving")
public class FileServer {

    public static void main(final String[] args) {
        Undertow server = Undertow.builder()
                .addHttpListener(8080, "localhost")
                .setHandler(resource(new FileResourceManager(new File(System.getProperty("user.home")), 100))
                        .setDirectoryListingEnabled(true))
                .build();
        server.start();
    }

}
