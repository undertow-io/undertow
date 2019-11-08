package io.undertow.server.handlers;

import java.util.Map;

import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.StreamSourceConduit;

import io.undertow.UndertowLogger;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.protocol.http.HttpServerConnection;
import io.undertow.util.ConduitFactory;
import io.undertow.util.HeaderValues;
import io.undertow.util.Methods;

/**
 * Handler that dumps a exchange to a log in curl format.
 *
 * @author Venkat Desu
 */
public class CurlRequestDumpingHandler implements HttpHandler
{
    private final HttpHandler next;
    private CurlRequestDumpingConduit dumpConduit;

    public RequestDumpInCurlFormatHandler(final HttpHandler next)
    {
        this.next = next;
    }

    public static boolean isPostOrPutMethod(HttpServerExchange exchange)
    {
        return (Methods.POST).equals(exchange.getRequestMethod()) || (Methods.PUT).equals(exchange.getRequestMethod());
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception
    {

        CURL_REQUEST_DUMP:
        {
            if (!UndertowLogger.REQUEST_DUMPER_LOGGER.isDebugEnabled() || !isPostOrPutMethod(exchange))
            {
                break CURL_REQUEST_DUMP;
            }

            final StringBuilder sb = new StringBuilder(2000);
            sb.append("curl '" + exchange.getRequestScheme() + ":/" + exchange.getDestinationAddress()
                    + exchange.getRequestURI());
            String queryString = exchange.getQueryString();
            if (exchange.getQueryString() != null && exchange.getQueryString().length() > 0)
            {
                sb.append("?" + queryString + "'");
            }
            else
            {
                sb.append("'");
            }

            Map<String, Cookie> cookies = exchange.getRequestCookies();
            if (cookies != null)
            {
                for(Map.Entry<String, Cookie> entry : cookies.entrySet())
                {
                    Cookie cookie = entry.getValue();
                    sb.append(" --cookie '" + cookie.getName() + "=" + cookie.getValue() + "'\n");
                }
            }
            for(HeaderValues header : exchange.getRequestHeaders())
            {
                for(String value : header)
                {
                    sb.append(" -H '" + header.getHeaderName() + ": " + value + "'");
                }
            }

            exchange.addRequestWrapper(new ConduitWrapper<StreamSourceConduit>()
            {
                @Override
                public StreamSourceConduit wrap(final ConduitFactory<StreamSourceConduit> factory,
                        final HttpServerExchange exchange)
                {
                    if (exchange.isRequestChannelAvailable() && !exchange.isResponseStarted())
                    {
                        ConduitStreamSourceChannel sourceChannel = ((HttpServerConnection) exchange.getConnection())
                                .getChannel().getSourceChannel();
                        dumpConduit = new CurlRequestDumpingConduit(sourceChannel.getConduit());
                        return dumpConduit;
                    }
                    return factory.create();
                }
            });

            exchange.addExchangeCompleteListener(new ExchangeCompletionListener()
            {
                @Override
                public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener)
                {
                    sb.append(" --data-binary '" + new String(dumpConduit.fullRequestDump()) + "'");

                    UndertowLogger.REQUEST_DUMPER_LOGGER.info(" CURL Request Format --> " + sb.toString());
                    nextListener.proceed();
                }
            });

        }// CURL_REQUEST_DUMP ends

        // Perform the exchange
        next.handleRequest(exchange);
    }

}
