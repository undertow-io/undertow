package io.undertow.websockets.jsr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.server.ServerEndpointConfiguration;

import io.undertow.UndertowLogger;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.util.ConcreteIoFuture;
import io.undertow.websockets.core.handler.WebSocketConnectionCallback;
import io.undertow.websockets.core.protocol.Handshake;
import io.undertow.websockets.spi.UpgradeCallback;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import org.xnio.FinishedIoFuture;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.Pool;

/**
 * @author Stuart Douglas
 */
public class JsrWebSocketServlet extends HttpServlet {


    private final Set<Handshake> handshakes;

    private final WebSocketConnectionCallback callback;

    public JsrWebSocketServlet(WebSocketConnectionCallback callback, ServerEndpointConfiguration... configs) {
        this.callback = callback;
        this.handshakes = handshakes(configs);
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {

        final ServletWebSocketHttpExchange facade = new ServletWebSocketHttpExchange(req, resp);
        Handshake handshaker = null;
        for (Handshake method : handshakes) {
            if (method.matches(facade)) {
                handshaker = method;
                break;
            }
        }

        if (handshaker == null) {
            UndertowLogger.REQUEST_LOGGER.debug("Could not find hand shaker for web socket request");
            resp.sendError(400);
            return;
        }
        handshaker.handshake(facade, callback);
    }

    protected Set<Handshake> handshakes(ServerEndpointConfiguration... configs) {
        Set<Handshake> handshakes = new HashSet<Handshake>();
        for (ServerEndpointConfiguration config : configs) {
            handshakes.add(new JsrHybi07Handshake(config));
            handshakes.add(new JsrHybi08Handshake(config));
            handshakes.add(new JsrHybi13Handshake(config));
        }
        return handshakes;
    }

    private static class ServletWebSocketHttpExchange implements WebSocketHttpExchange {

        private final HttpServletRequest request;
        private final HttpServletResponse response;

        private ServletWebSocketHttpExchange(final HttpServletRequest request, final HttpServletResponse response) {
            this.request = request;
            this.response = response;
        }


        @Override
        public String getRequestHeader(final String headerName) {
            return request.getHeader(headerName);
        }

        @Override
        public Map<String, List<String>> getRequestHeaders() {
            Map<String, List<String>> headers = new HashMap<>();
            final Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String header = headerNames.nextElement();
                final Enumeration<String> theHeaders = request.getHeaders(header);
                final List<String> vals = new ArrayList<>();
                headers.put(header, vals);
                while (theHeaders.hasMoreElements()) {
                    vals.add(theHeaders.nextElement());
                }

            }
            return Collections.unmodifiableMap(headers);
        }

        @Override
        public String getResponseHeader(final String headerName) {
            return response.getHeader(headerName);
        }

        @Override
        public Map<String, List<String>> getResponseHeaders() {
            Map<String, List<String>> headers = new HashMap<>();
            final Collection<String> headerNames = response.getHeaderNames();
            for (String header : headerNames) {
                headers.put(header, new ArrayList<String>(response.getHeaders(header)));
            }
            return Collections.unmodifiableMap(headers);
        }

        @Override
        public void setResponseHeaders(final Map<String, List<String>> headers) {
            for (String header : response.getHeaderNames()) {
                response.setHeader(header, null);
            }

            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                for (String val : entry.getValue()) {
                    response.addHeader(entry.getKey(), val);
                }
            }
        }

        @Override
        public void setResponseHeader(final String headerName, final String headerValue) {
            response.setHeader(headerName, headerValue);
        }

        @Override
        public void setResponesCode(final int code) {
            response.setStatus(code);
        }

        @Override
        public void upgradeChannel(final UpgradeCallback upgradeCallback) {
            HttpServletRequestImpl impl = HttpServletRequestImpl.getRequestImpl(request);
            HttpServerExchange exchange = impl.getExchange();
            exchange.upgradeChannel(new ExchangeCompletionListener() {
                @Override
                public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {
                    upgradeCallback.handleUpgrade(exchange.getConnection().getChannel(), exchange.getConnection().getBufferPool());
                }
            });
        }

        @Override
        public IoFuture<Void> sendData(final ByteBuffer data) {
            try {
                final ServletOutputStream outputStream = response.getOutputStream();
                while (data.hasRemaining()) {
                    outputStream.write(data.get());
                }
                return new FinishedIoFuture<Void>(null);
            } catch (IOException e) {
                final ConcreteIoFuture<Void> ioFuture = new ConcreteIoFuture<>();
                ioFuture.setException(e);
                return ioFuture;
            }
        }

        @Override
        public IoFuture<byte[]> readRequestData() {
            final ByteArrayOutputStream data = new ByteArrayOutputStream();
            try {
                final ServletInputStream in = request.getInputStream();
                byte[] buf = new byte[1024];
                int r;
                while ((r = in.read(buf)) != -1) {
                    data.write(buf, 0, r);
                }
                return new FinishedIoFuture<byte[]>(data.toByteArray());
            } catch (IOException e) {
                final ConcreteIoFuture<byte[]> ioFuture = new ConcreteIoFuture<>();
                ioFuture.setException(e);
                return ioFuture;
            }
        }


        @Override
        public void endExchange() {
            //noop
        }

        @Override
        public void close() {
            HttpServletRequestImpl impl = HttpServletRequestImpl.getRequestImpl(request);
            HttpServerExchange exchange = impl.getExchange();
            IoUtils.safeClose(exchange.getConnection());
        }

        @Override
        public String getRequestScheme() {
            return request.getScheme();
        }

        @Override
        public String getRequestURI() {
            return request.getRequestURI();
        }

        @Override
        public Pool<ByteBuffer> getBufferPool() {
            HttpServletRequestImpl impl = HttpServletRequestImpl.getRequestImpl(request);
            HttpServerExchange exchange = impl.getExchange();
            return exchange.getConnection().getBufferPool();
        }

        @Override
        public String getQueryString() {
            return request.getQueryString();
        }
    }
}
