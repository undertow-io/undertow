package io.undertow.proxy;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.xnio.Pooled;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import io.undertow.proxy.container.Balancer;
import io.undertow.proxy.container.Context;
import io.undertow.proxy.container.Context.Status;
import io.undertow.proxy.container.MCMConfig;
import io.undertow.proxy.container.Node;
import io.undertow.proxy.container.Node.NodeStatus;
import io.undertow.proxy.container.SessionId;
import io.undertow.proxy.container.VHost;
import io.undertow.proxy.mcmp.Constants;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.util.HttpString;

public class MCMPHandler implements HttpHandler {

    private String chost = "127.0.0.1";
    private int cport = 6666;
    private ProxyHandler proxy;

    private MCMConfig conf = null;
    private final ModClusterLoadBalancingProxyClient loadBalancer;

    private final HttpHandler next;
    public MCMPHandler(HttpHandler next, ModClusterLoadBalancingProxyClient loadBalancer) {
        this.next = next;
        this.loadBalancer = loadBalancer;
    }

    public void init() throws Exception {
        if (conf == null) {
            conf = new MCMConfig();
            conf.init();
            loadBalancer.setNodeservice(conf);
        }
        if (md == null)
            md = MessageDigest.getInstance("MD5");
        if (thread == null) {
            thread = new Thread(new MCMAdapterBackgroundProcessor(),
                    "MCMAdapaterBackgroundProcessor");
            thread.setDaemon(true);
            thread.start();

        }
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {

        /*
         * Proxy the request that needs to be proxied and process others
         */
        InetSocketAddress addr = exchange.getDestinationAddress();
        if (addr.getPort() != cport || !addr.getHostName().equals(chost)) {
            next.handleRequest(exchange);
            return;
        }

        HttpString method = exchange.getRequestMethod();
        try {
            if (method.equals(Constants.GET)) {
                // In fact that is /mod_cluster_manager
                process_manager(exchange);
            } else if (method.equals(Constants.CONFIG)) {
                process_config(exchange);
            } else if (method.equals(Constants.ENABLE_APP)) {
                try {
                    Map<String, String[]> params = read_post_parameters(exchange);
                    if (params == null) {
                        process_error(TYPESYNTAX, SMESPAR, exchange);
                        return;
                    }
                    process_enable(exchange, params);
                    process_OK(exchange);
                } catch (Exception Ex) {
                    Ex.printStackTrace(System.out);
                }
            } else if (method.equals(Constants.DISABLE_APP)) {
                Map<String, String[]> params = read_post_parameters(exchange);
                if (params == null) {
                    process_error(TYPESYNTAX, SMESPAR, exchange);
                    return;
                }
                process_disable(exchange, params);
                process_OK(exchange);
            } else if (method.equals(Constants.STOP_APP)) {
                Map<String, String[]> params = read_post_parameters(exchange);
                if (params == null) {
                    process_error(TYPESYNTAX, SMESPAR, exchange);
                    return;
                }
                process_stop(exchange, params);
                process_OK(exchange);
            } else if (method.equals(Constants.REMOVE_APP)) {
                try {
                    process_remove(exchange);
                } catch (Exception Ex) {
                    Ex.printStackTrace(System.out);
                }
            } else if (method.equals(Constants.STATUS)) {
                process_status(exchange);
            } else if (method.equals(Constants.DUMP)) {
                process_dump(exchange);
            } else if (method.equals(Constants.INFO)) {
                try {
                    process_info(exchange);
                } catch (Exception Ex) {
                    Ex.printStackTrace(System.out);
                }
            } else if (method.equals(Constants.PING)) {
                process_ping(exchange);
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
            exchange.setResponseCode(500);
            StreamSinkChannel resp = exchange.getResponseChannel();

            ByteBuffer bb = ByteBuffer.allocate(100);
            bb.put(e.toString().getBytes());
            bb.flip();

            try {
                resp.write(bb);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            exchange.endExchange();
            return;
        }
    }

    static String MOD_CLUSTER_EXPOSED_VERSION = "mod_cluster_undertow/0.0.0.Beta";
    /*
     * build the mod_cluster_manager page
     * It builds the html like mod_manager.c
     *
     */
    boolean checkNonce = true;
    boolean reduceDisplay = false;
    boolean allowCmd = true;
    boolean displaySessionids = true;
    private void process_manager(HttpServerExchange exchange) throws Exception {

        Map<String, Deque<String>> params = exchange.getQueryParameters();
        boolean hasNonce = params.containsKey("nonce");
        int refreshTime = 0;
        if (checkNonce) {
            /* Check the nonce */
            if (hasNonce) {
                String receivedNonce = params.get("nonce").getFirst();
                if (receivedNonce.equals(getRawNonce())) {
                    boolean refresh = params.containsKey("refresh");
                    if (refresh) {
                        String sval = params.get("refresh").getFirst();
                        refreshTime = Integer.parseInt(sval);
                        if (refreshTime < 10)
                            refreshTime = 10;
                        exchange.getResponseHeaders().add(new HttpString("Refresh"), Integer.toString(refreshTime));
                    }
                    boolean cmd = params.containsKey("Cmd");
                    boolean range = params.containsKey("Range");
                    if (cmd) {
                        String scmd = params.get("Cmd").getFirst();
                        if (scmd.equals("INFO")) {
                            process_info(exchange);
                            return;
                        } else if (scmd.equals("DUMP")) {
                            process_dump(exchange);
                            return;
                        } else if (scmd.equals("ENABLE-APP") && range) {
                            String srange = params.get("Range").getFirst();
                            Map<String, String[]> mparams = buildMap(params);
                            if (srange.equals("NODE")) {
                                process_node_cmd(exchange, mparams, Status.ENABLED);
                            }
                            if (srange.equals("DOMAIN")) {
                                boolean domain = params.containsKey("Domain");
                                if (domain) {
                                    String sdomain = params.get("Domain").getFirst();
                                    process_domain_cmd(exchange, sdomain, Status.ENABLED);
                                }
                            }
                            if (srange.equals("CONTEXT")) {
                                process_cmd(exchange, mparams, Status.ENABLED);
                            }
                         } else if (scmd.equals("DISABLE-APP") && range) {
                            String srange = params.get("Range").getFirst();
                            Map<String, String[]> mparams = buildMap(params);
                            if (srange.equals("NODE")) {
                                process_node_cmd(exchange, mparams, Status.DISABLED);
                            }
                            if (srange.equals("DOMAIN")) {
                                boolean domain = params.containsKey("Domain");
                                if (domain) {
                                    String sdomain = params.get("Domain").getFirst();
                                    process_domain_cmd(exchange, sdomain, Status.DISABLED);
                                }
                            }
                            if (srange.equals("CONTEXT")) {
                                process_cmd(exchange, mparams, Status.DISABLED);
                            }

                        }
                    }
                }
            }
        }

        exchange.setResponseCode(200);
        exchange.getResponseHeaders().add(new HttpString("Content-Type"), "text/html; charset=ISO-8859-1");
        StreamSinkChannel resp = exchange.getResponseChannel();
        StringBuilder buf = new StringBuilder();
        buf.append("<html><head>\n<title>Mod_cluster Status</title>\n</head><body>\n");
        buf.append("<h1>"+ MOD_CLUSTER_EXPOSED_VERSION + "</h1>");

        String uri = exchange.getRequestPath();
        String nonce = getNonce();
        if (refreshTime<=0)
            buf.append("<a href=\"" + uri + "?" + nonce +
                    "&refresh=10" +
                    "\">Auto Refresh</a>");

        buf.append(" <a href=\"" +  uri + "?" + nonce +
                "&Cmd=DUMP&Range=ALL" +
                "\">show DUMP output</a>");

        buf.append(" <a href=\"" + uri + "?" + nonce +
                "&Cmd=INFO&Range=ALL" +
                "\">show INFO output</a>");

        buf.append("\n");

        /* TODO sort the node by LBGroup (domain) */
        String lbgroup = "";
        for (Node node : conf.getNodes()) {
            if (!lbgroup.equals(node.getDomain())) {
                lbgroup = node.getDomain();
                if (reduceDisplay)
                    buf.append("<br/><br/>LBGroup " + lbgroup + ": ");
                else
                    buf.append("<h1> LBGroup " + lbgroup + ": ");
                if (allowCmd) {
                    domainCommandString(buf, uri, Status.ENABLED, lbgroup);
                    domainCommandString(buf, uri, Status.DISABLED, lbgroup);
                }
            }
            if (reduceDisplay) {
                buf.append("<br/><br/>Node " + node.getJvmRoute());
                printProxyStat(buf, node, reduceDisplay);
            } else
                buf.append("<h1> Node " + node.getJvmRoute() + " (" + node.getType() + "://" + node.getHostname() + ":" + node.getPort() + "): </h1>\n");


            if (allowCmd) {
                nodeCommandString(buf, uri, Status.ENABLED, node.getJvmRoute());
                nodeCommandString(buf, uri, Status.DISABLED, node.getJvmRoute());
            }
            if (!reduceDisplay) {
                buf.append("<br/>\n");
                buf.append("Balancer: " + node.getBalancer() + ",LBGroup: " + node.getDomain());
                String flushpackets = "off";
                if (node.isFlushpackets())
                    flushpackets = "Auto";
                buf.append(",Flushpackets: " + flushpackets + ",Flushwait: " + node.getFlushwait() + ",Ping: " + node.getPing() + " ,Smax: " + node.getPing() + ",Ttl: " + node.getTtl());
                printProxyStat(buf, node, reduceDisplay);
            } else {
                buf.append("<br/>\n");
            }
            // the sessionid list is mostly for demos.
            if (displaySessionids)
                buf.append(",Num sessions: " + conf.getJVMRouteSessionCount(node.getJvmRoute()));
            buf.append("\n");

            // Process the virtual-host of the node
            printInfoHost(buf, uri, reduceDisplay, allowCmd, node.getJvmRoute());
        }

       // Display the all the actives sessions
        if (displaySessionids)
            printInfoSessions(buf, conf.getSessionids());

        buf.append("</body></html>\n");
        ByteBuffer src = ByteBuffer.wrap(buf.toString().getBytes());
        resp.write(src);
        exchange.endExchange();
     }

    private void process_domain_cmd(HttpServerExchange exchange, String domain, Status status) throws Exception {
        for (Node node : conf.getNodes()) {
            if (node.getDomain().equals(domain)) {
                Map<String, String[]> params = new HashMap<String, String[]>();
                String[] values = new String[1];
                values[0] = node.getJvmRoute();
                params.put("JVMRoute", values);
                process_node_cmd(exchange, params, status);
            }
        }
     }

    private Map<String, String[]> buildMap(Map<String, Deque<String>> params) {
        Map<String, String[]> sparams = new HashMap<String, String[]>();
        for (String key : params.keySet()) {
            // In fact we only have one
            String[] values = new String[1];
            values[0] = params.get(key).getFirst();
            sparams.put(key, values);
        }
        return sparams;
    }

    /*
     * list the session informations.
     */
    private void printInfoSessions(StringBuilder buf, List<SessionId> sessionids) {
        buf.append("<h1>SessionIDs:</h1>");
        buf.append("<pre>");
        for (SessionId s : sessionids)
            buf.append("id: " + s.getSessionId() + " route: " + s.getJmvRoute() + "\n");
        buf.append("</pre>");
    }

    /* based on manager_info_hosts */
    private void printInfoHost(StringBuilder buf, String uri, boolean reduceDisplay, boolean allowCmd, String jvmRoute) {
        for (VHost host : conf.getHosts()) {
            if (host.getJVMRoute().equals(jvmRoute)) {
                if (!reduceDisplay) {
                    buf.append("<h2> Virtual Host " + host.getId() + ":</h2>");
                }
                printInfoContexts(buf, uri, reduceDisplay, allowCmd, host.getId(), host.getAliases(), jvmRoute);
                if (reduceDisplay) {
                    buf.append("Aliases: ");
                    for (String alias : host.getAliases())
                        buf.append(alias + " ");
                } else {
                    buf.append("<h3>Aliases:</h3>");
                    buf.append("<pre>");
                    for (String alias : host.getAliases())
                        buf.append(alias + "\n");
                    buf.append("</pre>");
                }

            }
        }

    }

    /* based on manager_info_contexts */
    private void printInfoContexts(StringBuilder buf, String uri, boolean reduceDisplay, boolean allowCmd, long host, String[] alias, String jvmRoute) {
        if (!reduceDisplay)
            buf.append("<h3>Contexts:</h3>");
        buf.append("<pre>");
        for (Context context : conf.getContexts()) {
            if (context.getJVMRoute().equals(jvmRoute) && context.getHostid() == host) {
                String status = "REMOVED";
                switch (context.getStatus()) {
                    case ENABLED:
                        status = "ENABLED";
                        break;
                    case DISABLED:
                        status = "DISABLED";
                        break;
                    case STOPPED:
                        status = "STOPPED";
                        break;
                }
                buf.append(context.getPath() + " , Status: " + status + " Request: " + context.getNbRequests() + " ");
                if (allowCmd)
                    contextCommandString(buf, uri, context.getStatus(), context.getPath(), alias, jvmRoute);
                buf.append("\n");
            }
        }
        buf.append("</pre>");
    }

    /* generate a command URL for the context */
    private void contextCommandString(StringBuilder buf, String uri, Status status, String path, String[] alias, String jvmRoute) {
        switch (status) {
            case DISABLED:
                buf.append("<a href=\"" + uri + "?" + getNonce() + "&Cmd=ENABLE-APP&Range=CONTEXT&");
                contextString(buf, path, alias, jvmRoute);
                buf.append("\">Enable</a> ");
                break;
            case ENABLED:
                buf.append("<a href=\"" + uri + "?" + getNonce() + "&Cmd=DISABLE-APP&Range=CONTEXT&");
                contextString(buf, path, alias, jvmRoute);
                buf.append("\">Disable</a> ");
                break;
        }
    }

    private void contextString(StringBuilder buf, String path, String[] alias, String jvmRoute) {
        buf.append("JVMRoute=" + jvmRoute + "&Alias=");
        boolean first = true;
        for (String a : alias) {
             if (first)
                first = false;
            else
                buf.append(",");
            buf.append(a);
        }
       buf.append("&Context=" + path);
    }

    private void nodeCommandString(StringBuilder buf, String uri, Status status, String jvmRoute) {
        switch(status) {
            case ENABLED:
                buf.append("<a href=\"" + uri + "?" + getNonce() + "&Cmd=ENABLE-APP&Range=NODE&JVMRoute=" + jvmRoute + "\">Enable Contexts</a> ");
                break;
            case DISABLED:
                buf.append("<a href=\"" + uri + "?" + getNonce() + "&Cmd=DISABLE-APP&Range=NODE&JVMRoute=" + jvmRoute + "\">Disable Contexts</a> ");
                break;
        }
    }

    private void printProxyStat(StringBuilder buf, Node node, boolean reduceDisplay ) {
        String status = "NOTOK";
        if (node.getStatus()==NodeStatus.NODE_UP)
            status = "OK";
        if (reduceDisplay)
            buf.append(" " + status + " ");
        else {
            buf.append(",Status: " + status + ",Elected: " + node.getOldelected() + ",Read: " + node.getRead() + ",Transferred: " + node.getTransfered() + ",Connected: "
                    + node.getConnected() + ",Load: " + node.getLoad());
        }
    }

    /* based on domain_command_string */
    private void domainCommandString(StringBuilder buf, String uri, Status status, String lbgroup) {
        switch(status) {
            case ENABLED:
                buf.append("<a href=\"" + uri + "?" + getNonce() + "&Cmd=ENABLE-APP&Range=DOMAIN&Domain=" + lbgroup + "\">Enable Nodes</a>");
                break;
            case DISABLED:
                buf.append("<a href=\"" + uri + "?" + getNonce() + "&Cmd=DISABLE-APP&Range=DOMAIN&Domain=" + lbgroup + "\">Disable Nodes</a>");
                break;
        }
    }

    private static final String VERSION_PROTOCOL = "0.2.1";
    private static final String TYPESYNTAX = "SYNTAX";
    private static final String TYPEMEM = "MEM";

    /* the syntax error messages */
    private static final String SMESPAR = "SYNTAX: Can't parse message";
    private static final String SBALBIG = "SYNTAX: Balancer field too big";
    private static final String SBAFBIG = "SYNTAX: A field is too big";
    private static final String SROUBIG = "SYNTAX: JVMRoute field too big";
    private static final String SROUBAD = "SYNTAX: JVMRoute can't be empty";
    private static final String SDOMBIG = "SYNTAX: LBGroup field too big";
    private static final String SHOSBIG = "SYNTAX: Host field too big";
    private static final String SPORBIG = "SYNTAX: Port field too big";
    private static final String STYPBIG = "SYNTAX: Type field too big";
    private static final String SALIBAD = "SYNTAX: Alias without Context";
    private static final String SCONBAD = "SYNTAX: Context without Alias";
    private static final String SBADFLD = "SYNTAX: Invalid field ";
    private static final String SBADFLD1 = " in message";
    private static final String SMISFLD = "SYNTAX: Mandatory field(s) missing in message";
    private static final String SCMDUNS = "SYNTAX: Command is not supported";
    private static final String SMULALB = "SYNTAX: Only one Alias in APP command";
    private static final String SMULCTB = "SYNTAX: Only one Context in APP command";
    private static final String SREADER = "SYNTAX: %s can't read POST data";

    /* the mem error messages */
    private static final String MNODEUI = "MEM: Can't update or insert node";
    private static final String MNODERM = "MEM: Old node still exist";
    private static final String MBALAUI = "MEM: Can't update or insert balancer";
    private static final String MNODERD = "MEM: Can't read node";
    private static final String MHOSTRD = "MEM: Can't read host alias";
    private static final String MHOSTUI = "MEM: Can't update or insert host alias";
    private static final String MCONTUI = "MEM: Can't update or insert context";

    static final byte[] CRLF = "\r\n".getBytes();

    protected Thread thread = null;
    private final String sgroup = "224.0.1.105";
    private final int sport = 23364;
    private final String slocal = "127.0.0.1";
    private MessageDigest md = null;
    private final String scheme = "http";
    private final String securityKey = System
            .getProperty("org.jboss.cluster.proxy.securityKey", "secret");

    protected class MCMAdapterBackgroundProcessor implements Runnable {

        /*
         * the messages to send are something like:
         *
         * HTTP/1.0 200 OK
         * Date: Thu, 13 Sep 2012 09:24:02 GMT
         * Sequence: 5
         * Digest: ae8e7feb7cd85be346134657de3b0661
         * Server: b58743ba-fd84-11e1-bd12-ad866be2b4cc
         * X-Manager-Address: 127.0.0.1:6666
         * X-Manager-Url: /b58743ba-fd84-11e1-bd12-ad866be2b4cc
         * X-Manager-Protocol: http
         * X-Manager-Host: 10.33.144.3
         * non-Javadoc)
         */
        @Override
        public void run() {
            try {
                InetAddress group = InetAddress.getByName(sgroup);
                InetAddress addr = InetAddress.getByName(slocal);
                InetSocketAddress addrs = new InetSocketAddress(sport);

                MulticastSocket s = new MulticastSocket(addrs);
                s.setTimeToLive(29);
                s.joinGroup(group);

                int seq = 0;
                /*
                 * apr_uuid_get(&magd->suuid);
                 * magd->srvid[0] = '/';
                 * apr_uuid_format(&magd->srvid[1], &magd->suuid);
                 * In fact we use the srvid on the 2 second byte [1]
                 */
                String server = UUID.randomUUID().toString();
                boolean ok = true;
                while (ok) {
                    Date date = new Date(System.currentTimeMillis());
                    md.reset();
                    digestString(md, securityKey);
                    byte[] ssalt = md.digest();
                    md.update(ssalt);
                    digestString(md, date);
                    digestString(md, seq);
                    digestString(md, server);
                    byte[] digest = md.digest();
                    StringBuilder str = new StringBuilder();
                    for (int i = 0; i < digest.length; i++)
                        str.append(String.format("%x", digest[i]));

                    String sbuf = "HTTP/1.0 200 OK\r\n" + "Date: " + date + "\r\n" + "Sequence: "
                            + seq + "\r\n" + "Digest: " + str.toString() + "\r\n" + "Server: "
                            + server + "\r\n" + "X-Manager-Address: " + getChost() + ":" + getCport()
                            + "\r\n" + "X-Manager-Url: /" + server + "\r\n"
                            + "X-Manager-Protocol: " + scheme + "\r\n" + "X-Manager-Host: " + getChost()
                            + "\r\n";

                    byte[] buf = sbuf.getBytes();
                    DatagramPacket data = new DatagramPacket(buf, buf.length, group, sport);
                    s.send(data);
                    Thread.sleep(1000);
                    seq++;
                }
                s.leaveGroup(group);
            } catch (Exception Ex) {
                Ex.printStackTrace(System.out);
            }
        }

        private void digestString(MessageDigest md, int seq) {
            String sseq = "" + seq;
            digestString(md, sseq);
        }

        private void digestString(MessageDigest md, Date date) {
            String sdate = date.toString();
            digestString(md, sdate);
        }

        private void digestString(MessageDigest md, String securityKey) {
            byte[] buf = securityKey.getBytes();
            md.update(buf);

        }

    }

    /**
     * Process <tt>PING</tt> request
     *
     * @param req
     * @param res
     * @throws Exception
     */
    private void process_ping(HttpServerExchange exchange) throws Exception {
        System.out.println("process_ping");
        Map<String, String[]> params = read_post_parameters(exchange);
        if (params == null) {
            process_error(TYPESYNTAX, SMESPAR, exchange);
            return;
        }
        String jvmRoute = null;
        String scheme = null;
        String host = null;
        String port = null;

        for (Map.Entry<String, String[]> e : params.entrySet()) {
            String name = e.getKey();
            String[] values =  e.getValue();
            String value = values[0];
            if (name.equalsIgnoreCase("JVMRoute")) {
                jvmRoute = value;
            } else if (name.equalsIgnoreCase("Scheme")) {
                scheme = value;
            } else if (name.equalsIgnoreCase("Port")) {
                port = value;
            } else if (name.equalsIgnoreCase("Host")) {
                host = value;
            } else {
                process_error(TYPESYNTAX, SBADFLD + name + SBADFLD1, exchange);
                return;
            }
        }
        if (jvmRoute == null) {
            if (scheme == null && host == null && port == null) {
                exchange.getResponseHeaders().add(new HttpString("Content-Type"), "text/plain");
                String data = "Type=PING-RSP&State=OK";
                StreamSinkChannel resp = exchange.getResponseChannel();
                ByteBuffer bb = ByteBuffer.allocate(data.length());
                bb.put(data.getBytes());
                bb.flip();
                resp.write(bb);
                   return;
            } else {
                if (scheme == null || host == null || port == null) {
                    process_error(TYPESYNTAX, SMISFLD, exchange);
                    return;
                }
                exchange.getResponseHeaders().add(new HttpString("Content-Type"), "text/plain");
                String data = "Type=PING-RSP";
                if (ishost_up(scheme, host, port))
                    data = data.concat("&State=OK");
                else
                    data = data.concat("&State=NOTOK");

                StreamSinkChannel resp = exchange.getResponseChannel();
                ByteBuffer bb = ByteBuffer.allocate(data.length());
                bb.put(data.getBytes());
                bb.flip();
                resp.write(bb);
            }
        } else {
            // ping the corresponding node.
            Node node = conf.getNode(jvmRoute);
            if (node == null) {
                process_error(TYPEMEM, MNODERD, exchange);
                return;
            }
            exchange.getResponseHeaders().add(new HttpString("Content-Type"), "text/plain");
            String data = "Type=PING-RSP";
            if (isnode_up(node))
                data = data.concat("&State=OK");
            else
                data = data.concat("&State=NOTOK");

            StreamSinkChannel resp = exchange.getResponseChannel();
            ByteBuffer bb = ByteBuffer.allocate(data.length());
            bb.put(data.getBytes());
            bb.flip();
            resp.write(bb);
        }
    }

    /*
     * TODO: this is a ugly hack. (it should even have channel.awaitReadable() to block until the whole MCM is received.
     * copied from io/undertow/server/handlers/form/FormEncodedDataHandler.java
     */
    private Map<String, String[]> read_post_parameters(HttpServerExchange exchange) throws IOException {
        final Map<String, String[]> ret = new HashMap<String, String[]>();
        FormData formData = handleEvent(exchange);
        Iterator<String> it = formData.iterator();
        while (it.hasNext()) {
            final String name = it.next();
            Deque<FormData.FormValue> val = formData.get(name);
            if (ret.containsKey(name)) {
                String[] existing = ret.get(name);
                String[] array = new String[val.size() + existing.length];
                System.arraycopy(existing, 0, array, 0, existing.length);
                int i = existing.length;
                for (final FormData.FormValue v : val) {
                    array[i++] = v.getValue();
                }
                ret.put(name, array);
            } else {
                String[] array = new String[val.size()];
                int i = 0;
                for (final FormData.FormValue v : val) {
                    array[i++] = v.getValue();
                }
                ret.put(name, array);
            }
        }
        return ret;
    }

    /* more code adapted from FormEncodedDataHandler (handleEvent) */
        public FormData handleEvent(HttpServerExchange exchange) {
            StreamSourceChannel channel = exchange.getRequestChannel();
            FormData data = new FormData(10);
            final Pooled<ByteBuffer> pooled = exchange.getConnection().getBufferPool().allocate();
            try {
                final ByteBuffer buffer = pooled.getResource();
                int state = 0;
                String name = null;
                StringBuilder builder = new StringBuilder();
                int c;
                do {
                    c = channel.read(buffer);
                    if (c > 0) {
                        buffer.flip();
                        while (buffer.hasRemaining()) {
                            byte n = buffer.get();
                            switch (state) {
                                case 0: {
                                    if (n == '=') {
                                        name = builder.toString();
                                        builder.setLength(0);
                                        state = 2;
                                    } else if (n == '%' || n == '+') {
                                        state = 1;
                                        builder.append((char) n);
                                    } else {
                                        builder.append((char) n);
                                    }
                                    break;
                                }
                                case 1: {
                                    if (n == '=') {
                                        name = URLDecoder.decode(builder.toString(), "UTF-8");
                                        builder.setLength(0);
                                        state = 2;
                                    } else {
                                        builder.append((char) n);
                                    }
                                    break;
                                }
                                case 2: {
                                    if (n == '&') {
                                        data.add(name, builder.toString());
                                        builder.setLength(0);
                                        state = 0;
                                    } else if (n == '%' || n == '+') {
                                        state = 3;
                                        builder.append((char) n);
                                    } else {
                                        builder.append((char) n);
                                    }
                                    break;
                                }
                                case 3: {
                                    if (n == '&') {
                                        data.add(name, URLDecoder.decode(builder.toString(), "UTF-8"));
                                        builder.setLength(0);
                                        state = 0;
                                    } else {
                                        builder.append((char) n);
                                    }
                                    break;
                                }
                            }
                        }
                    }
                } while (c > 0);
                if (c == -1) {
                    if (state == 2) {
                        data.add(name, builder.toString());
                    } else if (state == 3) {
                        data.add(name, URLDecoder.decode(builder.toString(), "UTF-8"));
                    }
                    state = 4;
                }
            } catch (IOException e) {
                System.out.println("Failed parsing: " + e);

            } finally {
                pooled.free();
            }

            return data;
        }

    private boolean isnode_up(Node node) {
        System.out.println("process_ping: " + node);
        return false;
    }

    private boolean ishost_up(String scheme, String host, String port) {
        System.out.println("process_ping: " + scheme + "://" + host + ":" + port);
        return false;
    }

    /*
     * Something like:
     *
     * Node: [1],Name: 368e2e5c-d3f7-3812-9fc6-f96d124dcf79,Balancer:
     * cluster-prod-01,LBGroup: ,Host: 127.0.0.1,Port: 8443,Type:
     * https,Flushpackets: Off,Flushwait: 10,Ping: 10,Smax: 21,Ttl: 60,Elected:
     * 0,Read: 0,Transfered: 0,Connected: 0,Load: 1 Vhost: [1:1:1], Alias:
     * default-host Vhost: [1:1:2], Alias: localhost Vhost: [1:1:3], Alias:
     * example.com Context: [1:1:1], Context: /myapp, Status: ENABLED
     */

    /**
     * Process <tt>INFO</tt> request
     *
     * @param req
     * @param res
     * @throws Exception
     */
    private void process_info(HttpServerExchange exchange) throws Exception {

        String data = process_info_string();
        exchange.setResponseCode(200);
        exchange.getResponseHeaders().add(new HttpString("Content-Type"), "text/plain");
        exchange.getResponseHeaders().add(new HttpString("Server"), "Mod_CLuster/0.0.0");

        StreamSinkChannel resp = exchange.getResponseChannel();
        ByteBuffer bb = ByteBuffer.allocate(data.length());
        bb.put(data.getBytes());
        bb.flip();

        resp.write(bb);
        exchange.endExchange();
    }

    private String process_info_string() {
        int i = 1;
        StringBuilder data = new StringBuilder();

        for (Node node : conf.getNodes()) {
            data.append("Node: [").append(i).append("],Name: ").append(node.getJvmRoute())
                    .append(",Balancer: ").append(node.getBalancer()).append(",LBGroup: ")
                    .append(node.getDomain()).append(",Host: ").append(node.getHostname())
                    .append(",Port: ").append(node.getPort()).append(",Type: ")
                    .append(node.getType()).append(",Flushpackets: ")
                    .append((node.isFlushpackets() ? "On" : "Off")).append(",Flushwait: ")
                    .append(node.getFlushwait()).append(",Ping: ").append(node.getPing())
                    .append(",Smax: ").append(node.getSmax()).append(",Ttl: ")
                    .append(node.getTtl()).append(",Elected: ").append(node.getElected())
                    .append(",Read: ").append(node.getRead()).append(",Transfered: ")
                    .append(node.getTransfered()).append(",Connected: ")
                    .append(node.getConnected()).append(",Load: ").append(node.getLoad() + "\n");
            i++;
        }

        for (VHost host : conf.getHosts()) {
            int j = 1;
            long node = conf.getNodeId(host.getJVMRoute());
            for (String alias : host.getAliases()) {
                data.append("Vhost: [").append(node).append(":").append(host.getId()).append(":")
                        .append(j).append("], Alias: ").append(alias).append("\n");

                j++;
            }
        }

        i = 1;
        for (Context context : conf.getContexts()) {
            data.append("Context: [").append(conf.getNodeId(context.getJVMRoute())).append(":")
                    .append(context.getHostid()).append(":").append(i).append("], Context: ")
                    .append(context.getPath()).append(", Status: ").append(context.getStatus())
                    .append("\n");
            i++;
        }
        return data.toString();
    }

    /*
     * something like:
     *
     * balancer: [1] Name: cluster-prod-01 Sticky: 1 [JSESSIONID]/[jsessionid]
     * remove: 0 force: 0 Timeout: 0 maxAttempts: 1 node: [1:1],Balancer:
     * cluster-prod-01,JVMRoute: 368e2e5c-d3f7-3812-9fc6-f96d124dcf79,LBGroup:
     * [],Host: 127.0.0.1,Port: 8443,Type: https,flushpackets: 0,flushwait:
     * 10,ping: 10,smax: 21,ttl: 60,timeout: 0 host: 1 [default-host] vhost: 1
     * node: 1 host: 2 [localhost] vhost: 1 node: 1 host: 3 [example.com] vhost:
     * 1 node: 1 context: 1 [/myapp] vhost: 1 node: 1 status: 1
     */

    /**
     * Process <tt>DUMP</tt> request
     *
     * @param exchange
     * @throws IOException
     */
    private void process_dump(HttpServerExchange exchange) throws IOException {
        String data = process_dump_string();
        exchange.setResponseCode(200);
        exchange.getResponseHeaders().add(new HttpString("Content-Type"), "text/plain");
        exchange.getResponseHeaders().add(new HttpString("Server"), "Mod_CLuster/0.0.0");

        StreamSinkChannel resp = exchange.getResponseChannel();
        ByteBuffer bb = ByteBuffer.allocate(data.length());
        bb.put(data.getBytes());
        bb.flip();

        resp.write(bb);
        exchange.endExchange();

    }
    private String process_dump_string() {
        StringBuilder data = new StringBuilder();
        int i = 1;
        for (Balancer balancer : conf.getBalancers()) {
            data.append("balancer: [" + i + "] Name: " + balancer.getName() + " Sticky: ")
                    .append((balancer.isStickySession() ? "1" : "0") + " [")
                    .append(balancer.getStickySessionCookie() + "]/[" + balancer.getStickySessionPath())
                    .append("] remove: " + (balancer.isStickySessionRemove() ? "1" : "0") + " force: ")
                    .append((balancer.isStickySessionForce() ? "1" : "0") + " Timeout: ")
                    .append(balancer.getWaitWorker() + " maxAttempts: " + balancer.getMaxattempts())
                    .append("\n");
            i++;
        }

        i  = 1;
        for (Node node : conf.getNodes()) {
            data.append("node: [").append(i).append(":").append(i).append("]")
                    .append(",Balancer: ").append(node.getBalancer())
                    .append(",JVMRoute: ").append(node.getJvmRoute())
                    .append(",LBGroup: ").append(node.getDomain())
                    .append(",Host: ").append(node.getHostname())
                    .append(",Port: ").append(node.getPort())
                    .append(",Type: ").append(node.getType())
                    .append(",flushpackets: ")
                    .append((node.isFlushpackets() ? "1" : "0")).append(",flushwait: ")
                    .append(node.getFlushwait()).append(",ping: ").append(node.getPing())
                    .append(",smax: ").append(node.getSmax()).append(",ttl: ")
                    .append(node.getTtl())
                    .append(",timeout: ").append(node.getTimeout())
                    .append("\n");
            i++;
        }

        for (VHost host : conf.getHosts()) {
            int j = 1;
            long node = conf.getNodeId(host.getJVMRoute());
            for (String alias : host.getAliases()) {
                data.append("host: ").append(j).append(" [")
                    .append(alias).append("] vhost: ").append(host.getId())
                    .append(" node: ").append(node).append("\n");

                j++;
            }
        }

        i = 1;
        for (Context context : conf.getContexts()) {
            long node = conf.getNodeId(context.getJVMRoute());
            data.append("context: ").append(i).append(" [").append(context.getPath())
                    .append("] vhost: ").append(context.getHostid())
                    .append(" node: ").append(node)
                    .append(" status: ").append(context.getStatus()).append("\n");

            i++;
        }

        return data.toString();
     }

    /**
     * Process <tt>STATUS</tt> request
     *
     * @param req
     * @param res
     * @throws Exception
     */
    private void process_status(HttpServerExchange exchange) throws Exception {
        Map<String, String[]> params = read_post_parameters(exchange);
        if (params == null) {
            process_error(TYPESYNTAX, SMESPAR, exchange);
            return;
        }
        String jvmRoute = null;
        String load = null;

        for (Map.Entry<String, String[]> e : params.entrySet()) {
            String name = e.getKey();
            String[] values =  e.getValue();
            String value = values[0];
            if (name.equalsIgnoreCase("JVMRoute")) {
                jvmRoute = value;
            } else if (name.equalsIgnoreCase("Load")) {
                load = value;
            } else {
                process_error(TYPESYNTAX, SBADFLD + value + SBADFLD1, exchange);
                return;
            }
        }
        if (load == null || jvmRoute == null) {
            process_error(TYPESYNTAX, SMISFLD, exchange);
            return;
        }

        Node node = conf.getNode(jvmRoute);
        if (node == null) {
            process_error(TYPEMEM, MNODERD, exchange);
            return;
        }
        node.setLoad(Integer.parseInt(load));
        /* TODO we need to check the node here */
        node.setStatus(Node.NodeStatus.NODE_UP);
        process_OK(exchange);
    }

    /**
     * Process <tt>REMOVE-APP</tt> request
     *
     * @param req
     * @param res
     * @throws Exception
     */
    private void process_remove(HttpServerExchange exchange) throws Exception {
        Map<String, String[]> params = read_post_parameters(exchange);
        if (params == null) {
            process_error(TYPESYNTAX, SMESPAR, exchange);
            return;
        }

        boolean global = false;
        if (exchange.getRequestPath().equals("*") || exchange.getRequestPath().endsWith("/*")) {
            global = true;
        }
        Context context = new Context();
        VHost host = new VHost();

        for (Map.Entry<String, String[]> e : params.entrySet()) {
            String name = e.getKey();
            String[] values = e.getValue();
            String value = values[0];
            if (name.equalsIgnoreCase("JVMRoute")) {
                if (conf.getNodeId(value) == -1) {
                    process_error(TYPEMEM, MNODERD, exchange);
                    return;
                }
                host.setJVMRoute(value);
                context.setJVMRoute(value);
            } else if (name.equalsIgnoreCase("Alias")) {
                // Alias is something like =default-host,localhost,example.com
                String[] aliases = value.split(",");
                host.setAliases(Arrays.asList(aliases));
            } else if (name.equalsIgnoreCase("Context")) {
                context.setPath(value);
            }

        }
        if (context.getJVMRoute() == null) {
            process_error(TYPESYNTAX, SROUBAD, exchange);
            return;
        }

        if (global)
            conf.removeNode(context.getJVMRoute());
        else
            conf.remove(context, host);
        process_OK(exchange);
    }

    /**
     * Process <tt>STOP-APP</tt> request
     *
     * @param req
     * @param res
     * @throws Exception
     */
    private void process_stop(HttpServerExchange exchange, Map<String, String[]> params) throws Exception {
        process_cmd(exchange, params, Context.Status.STOPPED);
    }

    /**
     * Process <tt>DISABLE-APP</tt> request
     *
     * @param req
     * @param res
     * @throws Exception
     */
    private void process_disable(HttpServerExchange exchange, Map<String, String[]> params) throws Exception {
        process_cmd(exchange, params, Context.Status.DISABLED);
    }

    /**
     * Process <tt>ENABLE-APP</tt> request
     *
     * @param req
     * @param res
     * @throws Exception
     */
    private void process_enable(HttpServerExchange exchange, Map<String, String[]> params) throws Exception {
        process_cmd(exchange, params, Context.Status.ENABLED);
    }

    private void process_cmd(HttpServerExchange exchange, Map<String, String[]> params, Context.Status status) throws Exception {
         if (exchange.getRequestPath().equals("*") || exchange.getRequestPath().endsWith("/*")) {
            process_node_cmd(exchange, params,  status);
            return;
        }

        Context context = new Context();
        VHost host = new VHost();

        for (Map.Entry<String, String[]> e : params.entrySet()) {
            String name = e.getKey();
            String[] values = e.getValue();
            String value = values[0];
            if (name.equalsIgnoreCase("JVMRoute")) {
                if (conf.getNodeId(value) == -1) {
                    process_error(TYPEMEM, MNODERD, exchange);
                    return;
                }
                host.setJVMRoute(value);
                context.setJVMRoute(value);
            } else if (name.equalsIgnoreCase("Alias")) {
                // Alias is something like =default-host,localhost,example.com
                String[] aliases = value.split(",");
                host.setAliases(Arrays.asList(aliases));
            } else if (name.equalsIgnoreCase("Context")) {
                context.setPath(value);
            }

        }
        if (context.getJVMRoute() == null) {
            process_error(TYPESYNTAX, SROUBAD, exchange);
            return;
        }
        context.setStatus(status);
        long id = conf.insertupdate(host);
        context.setHostid(id);
        conf.insertupdate(context);
    }

    /* Process a *-APP command that applies to the node */
    private void process_node_cmd(HttpServerExchange exchange, Map<String, String[]> params, Status status) throws Exception {
        String jvmRoute = null;
        for (Map.Entry<String, String[]> e : params.entrySet()) {
            String name = e.getKey();
            String[] values = e.getValue();
            String value = values[0];
            if (name.equalsIgnoreCase("JVMRoute")) {
                jvmRoute = value;
            }
        }
        if (jvmRoute == null) {
            process_error(TYPESYNTAX, SROUBAD, exchange);
            return;
        }

        for (VHost host : conf.getHosts()) {
            if (host.getJVMRoute().equals(jvmRoute)) {
                for (Context context : conf.getContexts()) {
                    if (context.getJVMRoute().equals(jvmRoute) && context.getHostid() == host.getId()) {
                        if (status != Status.REMOVED) {
                            context.setStatus(status);
                            conf.insertupdate(context);
                        } else {
                            conf.remove(context, host);
                        }
                    }
                }
            }
        }
    }

    /**
     * Process <tt>CONFIG</tt> request
     *
     * @param req
     * @param res
     * @throws Exception
     */
    private void process_config(HttpServerExchange exchange) throws Exception {
        Map<String, String[]> params = read_post_parameters(exchange);
        if (params == null) {
            process_error(TYPESYNTAX, SMESPAR, exchange);
            return;
        }

        Balancer balancer = new Balancer();
        Node node = new Node();

        for (Map.Entry<String, String[]> e : params.entrySet()) {
            String name = e.getKey();
            String[] values = e.getValue();
            String value = values[0];
            if (name.equalsIgnoreCase("Balancer")) {
                balancer.setName(value);
                node.setBalancer(value);
            } else if (name.equalsIgnoreCase("StickySession")) {
                if (value.equalsIgnoreCase("No"))
                    balancer.setStickySession(false);
            } else if (name.equalsIgnoreCase("StickySessionCookie")) {
                balancer.setStickySessionCookie(value);
            } else if (name.equalsIgnoreCase("StickySessionPath")) {
                balancer.setStickySessionPath(value);
            } else if (name.equalsIgnoreCase("StickySessionRemove")) {
                if (value.equalsIgnoreCase("Yes"))
                    balancer.setStickySessionRemove(true);
            } else if (name.equalsIgnoreCase("StickySessionForce")) {
                if (value.equalsIgnoreCase("no"))
                    balancer.setStickySessionForce(false);
            } else if (name.equalsIgnoreCase("WaitWorker")) {
                balancer.setWaitWorker(Integer.valueOf(value));
            } else if (name.equalsIgnoreCase("Maxattempts")) {
                balancer.setMaxattempts(Integer.valueOf(value));
            } else if (name.equalsIgnoreCase("JVMRoute")) {
                node.setJvmRoute(value);
            } else if (name.equalsIgnoreCase("Domain")) {
                node.setDomain(value);
            } else if (name.equalsIgnoreCase("Host")) {
                node.setHostname(value);
            } else if (name.equalsIgnoreCase("Port")) {
                node.setPort(Integer.valueOf(value));
            } else if (name.equalsIgnoreCase("Type")) {
                node.setType(value);
            } else if (name.equalsIgnoreCase("Reversed")) {
                continue; // ignore it.
            } else if (name.equalsIgnoreCase("flushpacket")) {
                if (value.equalsIgnoreCase("on"))
                    node.setFlushpackets(true);
                if (value.equalsIgnoreCase("auto"))
                    node.setFlushpackets(true);
            } else if (name.equalsIgnoreCase("flushwait")) {
                node.setFlushwait(Integer.valueOf(value));
            } else if (name.equalsIgnoreCase("ping")) {
                node.setPing(Integer.valueOf(value));
            } else if (name.equalsIgnoreCase("smax")) {
                node.setSmax(Integer.valueOf(value));
            } else if (name.equalsIgnoreCase("ttl")) {
                node.setTtl(Integer.valueOf(value));
            } else if (name.equalsIgnoreCase("Timeout")) {
                node.setTimeout(Integer.valueOf(value));
            } else {
                process_error(TYPESYNTAX, SBADFLD + name + SBADFLD1, exchange);
                return;
            }
        }

        conf.insertupdate(balancer);
        conf.insertupdate(node);
        process_OK(exchange);
    }

    /**
     * If the process is OK, then add 200 HTTP status and its "OK" phrase
     *
     * @param res
     * @throws Exception
     */
    private void process_OK(HttpServerExchange exchange) throws Exception {
        exchange.setResponseCode(200);
        exchange.getResponseHeaders().add(new HttpString("Content-type"), "plain/text");
        exchange.endExchange();
    }

    /**
     * If any error occurs,
     *
     * @param type
     * @param errstring
     * @param res
     * @throws Exception
     */
    private void process_error(String type, String errstring, HttpServerExchange exchange) throws Exception {
        exchange.setResponseCode(500);
        // res.setMessage("ERROR");
        exchange.getResponseHeaders().add(new HttpString("Version"), VERSION_PROTOCOL);
        exchange.getResponseHeaders().add(new HttpString("Type"), type);
        exchange.getResponseHeaders().add(new HttpString("Mess"), errstring);
        exchange.endExchange();
    }

    /* Nonce logic */
    private final Random r = new SecureRandom();
    private String nonce = null;
    String getNonce() {
        return "nonce=" + getRawNonce();
    }
    String getRawNonce() {
        if (this.nonce == null) {
            byte[] nonce = new byte[16];
            r.nextBytes(nonce);
            this.nonce = "";
            for (int i=0; i<16; i=i+2) {
                this.nonce = this.nonce.concat(Integer.toHexString(0xFF&nonce[i]*16 + 0xFF&nonce[i+1]));
            }
        }
        return nonce;
    }

    public String getChost() {
        return chost;
    }

    public void setChost(String chost) {
        this.chost = chost;
    }

    public int getCport() {
        return cport;
    }

    public void setCport(int cport) {
        this.cport = cport;
    }

    public ProxyHandler getProxy() {
        return proxy;
    }

    public void setProxy(ProxyHandler proxy) {
        this.proxy = proxy;
    }
}
