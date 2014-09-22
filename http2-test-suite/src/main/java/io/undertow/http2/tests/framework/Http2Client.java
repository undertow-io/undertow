/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.http2.tests.framework;

import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.client.http2.Http2ClientConnection;
import io.undertow.util.HeaderValues;
import org.junit.Assert;
import org.xnio.ChannelListener;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.channels.StreamSourceChannel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Stuart Douglas
 */
public class Http2Client {

    private final ClientConnection connection;

    public Http2Client(ClientConnection connection) {
        this.connection = connection;
        Assert.assertTrue(connection instanceof Http2ClientConnection);
    }

    public HttpResponse sendRequest(final ClientRequest request) throws IOException {
        final FutureResult<HttpResponse> result = new FutureResult<>();
        connection.sendRequest(request, new ClientCallback<ClientExchange>() {
            @Override
            public void completed(final ClientExchange exchange) {
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                exchange.setResponseListener(new ClientCallback<ClientExchange>() {
                    @Override
                    public void completed(final ClientExchange exchange) {
                        StreamSourceChannel source = exchange.getResponseChannel();
                        final ByteBuffer buffer = ByteBuffer.wrap(new byte[1024]);
                        for(;;) {
                            try {
                                int res = source.read(buffer);
                                if(res == -1) {
                                    handleDone(exchange, out);
                                    return;
                                } else if(res == 0) {
                                    source.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
                                        @Override
                                        public void handleEvent(StreamSourceChannel channel) {
                                            for(;;) {
                                                try {
                                                    int res = channel.read(buffer);
                                                    if (res == -1) {
                                                        handleDone(exchange, out);
                                                        return;
                                                    } else if (res == 0) {
                                                        return;
                                                    } else {
                                                        buffer.flip();
                                                        out.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.arrayOffset() + buffer.limit());
                                                        buffer.clear();
                                                    }
                                                } catch (IOException e) {
                                                    result.setException(e);
                                                }
                                            }
                                        }
                                    });
                                    source.resumeReads();
                                    return;
                                } else {
                                    buffer.flip();
                                    out.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.arrayOffset() + buffer.limit());
                                    buffer.clear();
                                }
                            } catch (IOException e) {
                                result.setException(e);
                            }
                        }
                    }

                    @Override
                    public void failed(IOException e) {
                        result.setException(e);
                    }
                });


            }

            @Override
            public void failed(IOException e) {
                result.setException(e);
            }

            private void handleDone(ClientExchange exchange, ByteArrayOutputStream out) {
                Map<String, List<String>> headers = new HashMap<>();
                ClientResponse response = exchange.getResponse();
                for(HeaderValues header : response.getResponseHeaders()) {
                    List<String> values = new ArrayList<String>();
                    for(String val : header) {
                        values.add(val);
                    }
                    headers.put(header.getHeaderName().toString(), Collections.unmodifiableList(values));
                }
                result.setResult(new HttpResponse(response.getResponseCode(), headers, out.toByteArray()));
            }
        });
        if(result.getIoFuture().await(10, TimeUnit.SECONDS) == IoFuture.Status.WAITING) {
            throw new IOException("Timed out");
        }
        return result.getIoFuture().get();
    }

}
