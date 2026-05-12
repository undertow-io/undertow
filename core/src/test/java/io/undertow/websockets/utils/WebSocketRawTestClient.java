package io.undertow.websockets.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;


public class WebSocketRawTestClient implements AutoCloseable {

    private final Socket socket;
    private final OutputStream out;
    private final InputStream in;

    public WebSocketRawTestClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.socket.setSoTimeout(5000); // 5s read timeout
        this.out = socket.getOutputStream();
        this.in = socket.getInputStream();
    }

    /**
     * Send HTTP Upgrade request and return the response status code.
     * Returns 101 on successful upgrade, 403 on Origin rejection, etc.
     */
    public UpgradeResult upgrade(String path, String origin) throws IOException {
        String wsKey = WebSocketPayloadUtil.generateWebSocketKey();
        byte[] request = WebSocketPayloadUtil.buildUpgradeRequest(
                "localhost", socket.getPort(), path, origin, wsKey);
        out.write(request);
        out.flush();

        // Read HTTP response (blocking until headers complete)
        StringBuilder response = new StringBuilder();
        int prev = 0;
        int curr;
        while ((curr = in.read()) != -1) {
            response.append((char) curr);
            // Detect end of HTTP headers: \r\n\r\n
            if (prev == '\n' && curr == '\r') {
                int next = in.read();
                if (next == '\n') {
                    response.append("\r\n");
                    break;
                }
                response.append((char) next);
            }
            prev = curr;
        }

        String responseStr = response.toString();
        int statusCode = parseStatusCode(responseStr);
        String acceptKey = parseHeader(responseStr, "Sec-WebSocket-Accept");

        return new UpgradeResult(statusCode, responseStr, acceptKey, wsKey);
    }

    /**
     * Send HTTP Upgrade request with permessage-deflate extension negotiation.
     */
    public UpgradeResult upgradeWithDeflate(String path, String origin) throws IOException {
        String wsKey = WebSocketPayloadUtil.generateWebSocketKey();
        byte[] request = WebSocketPayloadUtil.buildDeflateUpgradeRequest(
                "localhost", socket.getPort(), path, origin, wsKey);
        out.write(request);
        out.flush();

        StringBuilder response = new StringBuilder();
        int prev = 0;
        int curr;
        while ((curr = in.read()) != -1) {
            response.append((char) curr);
            if (prev == '\n' && curr == '\r') {
                int next = in.read();
                if (next == '\n') {
                    response.append("\r\n");
                    break;
                }
                response.append((char) next);
            }
            prev = curr;
        }

        String responseStr = response.toString();
        int statusCode = parseStatusCode(responseStr);
        String acceptKey = parseHeader(responseStr, "Sec-WebSocket-Accept");

        return new UpgradeResult(statusCode, responseStr, acceptKey, wsKey);
    }

    /**
     * Check if the upgrade response confirms permessage-deflate was negotiated.
     */
    public static boolean isDeflateNegotiated(UpgradeResult result) {
        return result.rawResponse().toLowerCase().contains("permessage-deflate");
    }

    /**
     * Send raw bytes directly to the socket (post-upgrade).
     */
    public void sendRaw(byte[] data) throws IOException {
        out.write(data);
        out.flush();
    }

    /**
     * Send a masked WebSocket text frame.
     */
    public void sendText(String message) throws IOException {
        byte[] frame = WebSocketPayloadUtil.buildFrame(
                WebSocketPayloadUtil.OP_TEXT, true,
                message.getBytes(StandardCharsets.UTF_8));
        sendRaw(frame);
    }

    /**
     * Read available bytes from the socket (non-blocking if timeout set).
     */
    public byte[] readAvailable() throws IOException {
        byte[] buf = new byte[4096];
        int read = in.read(buf);
        if (read <= 0)
            return new byte[0];
        byte[] result = new byte[read];
        System.arraycopy(buf, 0, result, 0, read);
        return result;
    }

    public String readAvailableresponse() throws IOException {
        if (this.in.available() > 0) {
            StringBuilder response = new StringBuilder();
            int prev = 0;
            int curr;
            while ((curr = in.read()) != -1) {
                response.append((char) curr);
                if (prev == '\n' && curr == '\r') {
                    int next = in.read();
                    if (next == '\n') {
                        response.append("\r\n");
                        break;
                    }
                    response.append((char) next);
                }
                prev = curr;
            }

            final String responseStr = response.toString();
            return responseStr;
        }
        return null;
    }

    public RawFrame readAvailableFrame(final int timeoutMs) throws IOException {
        if (availableInput()) {
            socket.setSoTimeout(100); // fast polling
            final long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                try {
                    final int b = in.read();
                    if (b == -1)
                        break;
                    // TODO: FIN and mask?
                    final int opCode = (b & 0x0F);
                    final int len = in.read() & 0x7F;
                    if (len < 126) {
                        final byte[] data = readNBytes(in, len);
                        if (opCode == 8) {// TODO: more ?
                            if (len > 2) {
                                int statuCode = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                                return new RawFrame(opCode, statuCode, len, new String(data, 1, data.length - 2));
                            } else {
                                throw new IOException();
                            }
                        } else {
                            return new RawFrame(opCode, -1, len, new String(data));
                        }
                    } else {
                        // TODO: extended payload
                        throw new IOException("Not supported");
                    }
                } catch (java.net.SocketTimeoutException e) {
                    // No data available, continue polling
                }
            }

            socket.setSoTimeout(5000); // restore
        }
        return null;
    }

    public boolean availableInput() throws IOException {
        return this.in.available() > 0;
    }
    /**
     * Count Pong frames received within a timeout period.
     */
    public int countPongFrames(long timeoutMs) throws IOException {
        int pongs = 0;
        long deadline = System.currentTimeMillis() + timeoutMs;
        socket.setSoTimeout(100); // fast polling

        while (System.currentTimeMillis() < deadline) {
            try {
                int b = in.read();
                if (b == -1) break;
                // Pong frame starts with 0x8A (FIN=1, opcode=0x0A)
                if ((b & 0x0F) == 0x0A) {
                    pongs++;
                    // Skip rest of pong frame (length byte + payload)
                    int len = in.read() & 0x7F;
                    readNBytes(in, len);
                }
            } catch (java.net.SocketTimeoutException e) {
                // No data available, continue polling
            }
        }

        socket.setSoTimeout(5000); // restore
        return pongs;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static int parseStatusCode(String response) {
        // HTTP/1.1 101 Switching Protocols
        try {
            return Integer.parseInt(response.split(" ")[1]);
        } catch (Exception e) {
            return -1;
        }
    }

    private static String parseHeader(String response, String headerName) {
        for (String line : response.split("\r\n")) {
            if (line.toLowerCase().startsWith(headerName.toLowerCase() + ":")) {
                return line.substring(headerName.length() + 1).trim();
            }
        }
        return null;
    }

    private static byte[] readNBytes(InputStream in, int len) throws IOException {
        if (len < 0) {
            throw new IllegalArgumentException("len < 0");
        }

        byte[] data = new byte[len];
        int read = 0;
        int n;
        while (read < len && (n = in.read(data, read, len - read)) != -1) {
            read += n;
        }

        return (read == len) ? data : Arrays.copyOf(data, read);
    }

    /*public record UpgradeResult(int statusCode, String rawResponse,
                                 String acceptKey, String wsKey) {
        public boolean isUpgraded() {
            return statusCode == 101;
        }

        public boolean isRejected() {
            return statusCode == 403;
        }
    }*/

    public final class UpgradeResult {
        private final int statusCode;
        private final String rawResponse;
        private final String acceptKey;
        private final String wsKey;

        // Canonical constructor
        public UpgradeResult(int statusCode, String rawResponse, String acceptKey, String wsKey) {
            this.statusCode = statusCode;
            this.rawResponse = rawResponse;
            this.acceptKey = acceptKey;
            this.wsKey = wsKey;
        }

        // Accessor methods (matching record style for API compatibility)
        public int statusCode() {
            return this.statusCode;
        }

        public String rawResponse() {
            return this.rawResponse;
        }

        public String acceptKey() {
            return this.acceptKey;
        }

        public String wsKey() {
            return this.wsKey;
        }

        // Custom methods
        public boolean isUpgraded() {
            return this.statusCode == 101;
        }

        public boolean isRejected() {
            return this.statusCode == 403;
        }

        // Record-style equals implementation
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UpgradeResult wedding = (UpgradeResult) o;
            return this.statusCode == wedding.statusCode &&
                    Objects.equals(this.rawResponse, wedding.rawResponse) &&
                    Objects.equals(this.acceptKey, wedding.acceptKey) &&
                    Objects.equals(this.wsKey, wedding.wsKey);
        }

        // Record-style hashCode implementation
        @Override
        public int hashCode() {
            return Objects.hash(statusCode, rawResponse, acceptKey, wsKey);
        }

        // Record-style toString implementation
        @Override
        public String toString() {
            return "UpgradeResult[" +
                    "statusCode=" + statusCode +
                    ", rawResponse='" + rawResponse + '\'' +
                    ", acceptKey='" + acceptKey + '\'' +
                    ", wsKey='" + wsKey + '\'' +
                    ']';
        }
    }

    /*public record RawFrame(int opCode, int statusCode,  int length, String rawContent) {
        public boolean isText() {
            return opCode == 1;
        }

        public boolean isBinary() {
            return opCode == 2;
        }

        public boolean isClose() {
            return opCode == 8;
        }

        public boolean isPing() {
            return opCode == 9;
        }

        public boolean isPong() {
            return opCode == 10;
        }
    }*/

    public final class RawFrame {
        private final int opCode;
        private final int statusCode;
        private final int length;
        private final String rawContent;

        // Canonical constructor
        public RawFrame(int opCode, int statusCode, int length, String rawContent) {
            this.opCode = opCode;
            this.statusCode = statusCode;
            this.length = length;
            this.rawContent = rawContent;
        }

        // Accessor methods (matching record style for API compatibility)
        public int opCode() {
            return this.opCode;
        }

        public int statusCode() {
            return this.statusCode;
        }

        public int length() {
            return this.length;
        }

        public String rawContent() {
            return this.rawContent;
        }

        // Custom helper methods
        public boolean isText() {
            return this.opCode == 1;
        }

        public boolean isBinary() {
            return this.opCode == 2;
        }

        public boolean isClose() {
            return this.opCode == 8;
        }

        public boolean isPing() {
            return this.opCode == 9;
        }

        public boolean isPong() {
            return this.opCode == 10;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RawFrame rawFrame = (RawFrame) o;
            return this.opCode == rawFrame.opCode &&
                    this.statusCode == rawFrame.statusCode &&
                    this.length == rawFrame.length &&
                    Objects.equals(this.rawContent, rawFrame.rawContent);
        }

        @Override
        public int hashCode() {
            return Objects.hash(opCode, statusCode, length, rawContent);
        }

        @Override
        public String toString() {
            return "RawFrame[" +
                    "opCode=" + opCode +
                    ", statusCode=" + statusCode +
                    ", length=" + length +
                    ", rawContent='" + rawContent + '\'' +
                    ']';
        }
    }
}