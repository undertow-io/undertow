package io.undertow.ajp;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;
import junit.framework.Assert;
import org.junit.Test;
import org.xnio.IoUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * @author Stuart Douglas
 */
public class AjpParsingUnitTestCase {

    private static final ByteBuffer buffer;

    static {
        final InputStream stream = AjpParsingUnitTestCase.class.getResourceAsStream("sample-ajp-request");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int r = 0;
            byte[] buf = new byte[1024];
            while ((r = stream.read(buf)) > 0) {
                out.write(buf, 0, r);
            }
            buffer = ByteBuffer.wrap(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            IoUtils.safeClose(stream);
        }
    }


    @Test
    public void testAjpParsing() {
        final ByteBuffer buffer = AjpParsingUnitTestCase.buffer.duplicate();
        HttpServerExchange result = new HttpServerExchange(null, null, null, null, null);
        final AjpParseState state = new AjpParseState();
        AjpParser.INSTANCE.parse(buffer, state, result);
        Assert.assertEquals(165, state.dataSize);
        Assert.assertTrue(state.isComplete());
        Assert.assertEquals(0, buffer.remaining());
        testResult(result);

    }

    @Test
    public void testByteByByteAjpParsing() {
        final ByteBuffer buffer = AjpParsingUnitTestCase.buffer.duplicate();

        HttpServerExchange result = new HttpServerExchange(null, null, null, null, null);
        final AjpParseState state = new AjpParseState();
        int limit = buffer.limit();
        for (int i = 1; i <= limit; ++i) {
            buffer.limit(i);
            AjpParser.INSTANCE.parse(buffer, state, result);
        }
        Assert.assertEquals(165, state.dataSize);
        Assert.assertTrue(state.isComplete());
        testResult(result);

    }

    private void testResult(final HttpServerExchange exchange) {
        Assert.assertSame(Methods.GET, exchange.getRequestMethod());
        Assert.assertEquals(Protocols.HTTP_1_1, exchange.getProtocol());
        Assert.assertEquals(3, exchange.getRequestHeaders().getHeaderNames().size());
        Assert.assertEquals("localhost:7777", exchange.getRequestHeaders().getFirst(Headers.HOST));
        Assert.assertEquals("Apache-HttpClient/4.1.3 (java 1.5)", exchange.getRequestHeaders().getFirst(Headers.USER_AGENT));
        Assert.assertEquals("Keep-Alive", exchange.getRequestHeaders().getFirst(Headers.CONNECTION));
    }
}
