package io.undertow.websockets.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;


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
        return result.getRawResponse().toLowerCase().contains("permessage-deflate");
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
                        final byte[] data = in.readNBytes(len);
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
                    in.readNBytes(len);
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

    public static class UpgradeResult {
        private int statusCode;
        private String rawResponse;
        private String acceptKey;
        private String wsKey;

        UpgradeResult(int statusCode, String rawResponse, String acceptKey, String wsKey) {
            this.statusCode = statusCode;
            this.rawResponse = rawResponse;
            this.acceptKey = acceptKey;
            this.wsKey = wsKey;
        }

        public boolean isUpgraded() {
            return statusCode == 101;
        }

        public boolean isRejected() {
            return statusCode == 403;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public void setStatusCode(int statusCode) {
            this.statusCode = statusCode;
        }

        public String getRawResponse() {
            return rawResponse;
        }

        public void setRawResponse(String rawResponse) {
            this.rawResponse = rawResponse;
        }

        public String getAcceptKey() {
            return acceptKey;
        }

        public void setAcceptKey(String acceptKey) {
            this.acceptKey = acceptKey;
        }

        public String getWsKey() {
            return wsKey;
        }

        public void setWsKey(String wsKey) {
            this.wsKey = wsKey;
        }

        @Override
        public String toString() {
            return "UpgradeResult [statusCode=" + statusCode + ", rawResponse=" + rawResponse + ", acceptKey=" + acceptKey
                    + ", wsKey=" + wsKey + "]";
        }

    }

    public static class RawFrame {
        private int opCode;
        private int statusCode;
        private int length;
        private String rawContent;
        RawFrame(int opCode, int statusCode,  int length, String rawContent) {
            this.opCode = opCode;
            this.statusCode = statusCode;
            this.length = length;
            this.rawContent = rawContent;
        }
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
        public int getOpCode() {
            return opCode;
        }
        public void setOpCode(int opCode) {
            this.opCode = opCode;
        }
        public int getStatusCode() {
            return statusCode;
        }
        public void setStatusCode(int statusCode) {
            this.statusCode = statusCode;
        }
        public int getLength() {
            return length;
        }
        public void setLength(int length) {
            this.length = length;
        }
        public String getRawContent() {
            return rawContent;
        }
        public void setRawContent(String rawContent) {
            this.rawContent = rawContent;
        }
        @Override
        public String toString() {
            return "RawFrame [opCode=" + opCode + ", statusCode=" + statusCode + ", length=" + length + ", rawContent="
                    + rawContent + "]";
        }

    }
}