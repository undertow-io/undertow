package io.undertow.server.ssl;

import io.undertow.Undertow;
import io.undertow.testutils.DefaultServer;
import io.undertow.util.Headers;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import org.junit.Assume;
import org.junit.Test;
import org.xnio.Options;
import org.xnio.Sequence;

public class TLS13HalfCloseHangTestCase {

  private static final Pattern CONTENT_LENGTH_PATTERN = Pattern
      .compile("Content-Length: ([0-9]+)", Pattern.CASE_INSENSITIVE);

  @Test
  public void testHang() throws IOException, GeneralSecurityException, InterruptedException {
    SSLContext clientSslContext = null;
    try {
      clientSslContext = DefaultServer.createClientSslContext("TLSv1.3");
    } catch (Throwable e) {
      // Don't try to run test if TLS 1.3 is not supported
      Assume.assumeNoException(e);
    }

    Undertow server = Undertow.builder()
        // This relies on TLSv1.2 context actually supporting TLS 1.3 which works fine with JDK11
        .addHttpsListener(0, "localhost", DefaultServer.getServerSslContext())
        .setHandler((exchange) -> {
          exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
          exchange.getResponseSender().send("Hello World!\n");
        })
        .setSocketOption(Options.SSL_ENABLED_PROTOCOLS, Sequence.of("TLSv1.3"))
        // These make the issue easier to detect
        .setIoThreads(1)
        .setWorkerThreads(1)
        .build();

    server.start();

    InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
    String uri = "https://localhost:" + address.getPort() + "/foo";

    doRequest(clientSslContext, address);

    doRequest(clientSslContext, address);

    server.stop();
    // sleep 1 s to prevent BindException (Address already in use) when running the CI
    try {
      Thread.sleep(1000);
    } catch (InterruptedException ignore) {}
  }

  private void doRequest(SSLContext clientSslContext, InetSocketAddress address)
      throws IOException {
    Socket rawSocket = new Socket();
    rawSocket.connect(address);
    SSLSocket sslSocket = (SSLSocket) clientSslContext.getSocketFactory()
        .createSocket(rawSocket, "localhost", address.getPort(), false);
    PrintWriter writer = new PrintWriter(sslSocket.getOutputStream());
    writer.println("GET / HTTP/1.1");
    writer.println("Host: localhost");
    writer.println("Connection: keep-alive");
    writer.println();
    writer.flush();
    readResponse(sslSocket);

    sslSocket.shutdownOutput();
    rawSocket.close();
  }

  private String readLine(InputStream is) throws IOException {
    StringBuilder line = new StringBuilder();
    while (true) {
      int c = is.read();
      switch (c) {
        case -1:
          throw new RuntimeException("Unexpected EOF");
        case '\r':
          continue;
        case '\n':
          return line.toString();

        default:
          line.append((char) c);
      }
    }
  }

  private void readResponse(SSLSocket sslSocket) throws IOException {
    String line;
    int contentLength = 0;

    do {
      line = readLine(sslSocket.getInputStream());
      Matcher matcher = CONTENT_LENGTH_PATTERN.matcher(line);
      if (matcher.matches()) {
        contentLength = Integer.parseInt(matcher.group(1), 10);
      }
    } while (!line.isEmpty());

    for (int i = 0; i < contentLength; i++) {
      sslSocket.getInputStream().read();
    }
  }
}
