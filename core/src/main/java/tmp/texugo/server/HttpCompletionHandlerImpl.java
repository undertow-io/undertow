package tmp.texugo.server;

import org.xnio.ChannelListener;
import org.xnio.channels.StreamSourceChannel;
import tmp.texugo.TexugoLogger;
import tmp.texugo.util.Headers;


/**
 * @author Stuart Douglas
 */
class HttpCompletionHandlerImpl implements HttpCompletionHandler {

    private final HttpServerExchange exchange;
    private final ChannelListener<? extends StreamSourceChannel> listener;

    HttpCompletionHandlerImpl(HttpServerExchange exchange, ChannelListener<? extends  StreamSourceChannel> listener) {
        this.exchange = exchange;
        this.listener = listener;
    }

    @Override
    public void handleComplete() {

        if(exchange.isResponseChannelAvailable()) {
            if(!exchange.isRequestChannelAvailable()) {
                TexugoLogger.REQUEST_LOGGER.getRequestCalledWithoutGetResponse();
            }
            //getResponseChannel() has not been called, this means we need to automatically write headers and
            //close the request
            if(exchange.isHttp11()) {
                //we set a content length of zero
                exchange.getResponseHeaders().add(Headers.CONTENT_LENGTH, "0");
            }
            exchange.startResponse(exchange.isCloseConnection());
        }

        final StreamSourceChannel underlyingRequestChannel = exchange.getUnderlyingRequestChannel();
        underlyingRequestChannel.getReadSetter().set((ChannelListener)listener);
        underlyingRequestChannel.resumeReads();
    }
}
