package net.instant.ws;

import java.io.File;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.instant.api.API1;
import net.instant.api.RequestHook;
import net.instant.util.Formats;
import net.instant.util.StringSigner;
import net.instant.util.Util;
import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.exceptions.InvalidHandshakeException;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.HandshakeBuilder;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;

public class InstantWebSocketServer extends WebSocketServer
        implements DraftWrapper.Hook {

    private static final Logger LOGGER = Logger.getLogger("IWSServer");

    private static final String K_KEYFILE = "instant.cookies.keyfile";
    private static final String K_CREATE = "instant.cookies.keyfile.create";

    public static final List<Draft> DEFAULT_DRAFTS;

    static {
        List<Draft> l =  new ArrayList<Draft>();
        l.add(new Draft_SSE());
        l.add(new Draft_Raw());
        l.addAll(WebSocketImpl.defaultdraftlist);
        l.add(new Draft_Error());
        DEFAULT_DRAFTS = Collections.unmodifiableList(l);
    }

    private final Set<RequestHook> hooks;
    private final Set<RequestHook> internalHooks;
    private final Map<WebSocket, RequestHook> assignments;
    private InformationCollector collector;
    private CookieHandler cookies;
    private ConnectionGC gc;
    private PrintStream httpLog;

    public InstantWebSocketServer(API1 api, InetSocketAddress addr) {
        super(addr, wrapDrafts(DEFAULT_DRAFTS));
        hooks = new LinkedHashSet<RequestHook>();
        internalHooks = new LinkedHashSet<RequestHook>();
        assignments = Collections.synchronizedMap(
            new HashMap<WebSocket, RequestHook>());
        collector = new InformationCollector(this);
        gc = new ConnectionGC(api);
        cookies = new CookieHandler(makeStringSigner(api));
        httpLog = System.err;
        for (Draft d : getDraft()) {
            if (d instanceof DraftWrapper)
                ((DraftWrapper) d).setHook(this);
        }
    }
    public InstantWebSocketServer(API1 api, int port) {
        this(api, new InetSocketAddress(port));
    }

    protected StringSigner makeStringSigner(API1 api) {
        String keypath = api.getConfiguration(K_KEYFILE);
        File keyfile = (keypath == null) ? null : new File(keypath);
        boolean create = Util.isTrue(api.getConfiguration(K_CREATE));
        if (keyfile != null) {
            return StringSigner.getInstance(keyfile, create);
        } else {
            return StringSigner.getInstance(null);
        }
    }

    public CookieHandler getCookieHandler() {
        return cookies;
    }
    public void setCookieHandler(CookieHandler c) {
        cookies = c;
    }

    public ConnectionGC getConnectionGC() {
        return gc;
    }
    public void setConnectionGC(ConnectionGC g) {
        gc = g;
    }

    public PrintStream getHTTPLog() {
        return httpLog;
    }
    public void setHTTPLog(PrintStream s) {
        httpLog = s;
    }

    /* Calling order:
     * 1. handleRequestLine (via DraftWrapper from translateHandshake)
     * 2. onWebsocketHandshakeReceivedAsServer (directly)
     * 3. postProcess (via DraftWrapper from
     *    postProcessHandshakeResponseAsServer) */
    public void handleRequestLine(Handshakedata handshake, String line) {
        collector.addRequestLine(handshake, line);
    }

    /* That method name is impressive. */
    @Override
    public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(
            WebSocket conn, Draft draft, ClientHandshake request)
            throws InvalidDataException {
        collector.addRequestData(conn, draft, request);
        return super.onWebsocketHandshakeReceivedAsServer(
            conn, draft, request);
    }

    public void postProcess(ClientHandshake request,
            ServerHandshakeBuilder response, HandshakeBuilder result)
            throws InvalidHandshakeException {
        Datum d = collector.addResponse(request, response, result);
        for (RequestHook h : getAllHooks()) {
            try {
                if (h.evaluateRequest(d, d)) {
                    assignments.put(d.getConnection(), h);
                    collector.postProcess(d);
                    httpLog.println(Formats.formatHTTPLog(d));
                    return;
                }
            } catch (Exception exc) {
                LOGGER.log(Level.SEVERE, "Exception while processing " +
                    "request " + d, exc);
                throw exc;
            }
        }
        throw new InvalidHandshakeException("try another draft");
    }

    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        RequestHook h = assignments.get(conn);
        Datum d = collector.get(conn);
        if (h != null) h.onOpen(d);
    }

    public void onMessage(WebSocket conn, String message) {
        RequestHook h = assignments.get(conn);
        Datum d = collector.get(conn);
        if (h != null) h.onInput(d, message);
    }

    public void onMessage(WebSocket conn, ByteBuffer message) {
        RequestHook h = assignments.get(conn);
        Datum d = collector.get(conn);
        if (h != null) h.onInput(d, message);
    }

    public void onClose(WebSocket conn, int code, String reason,
                        boolean remote) {
        RequestHook h = assignments.get(conn);
        Datum d = collector.get(conn);
        try {
            if (h != null)
                h.onClose(d, (code == CloseFrame.NORMAL ||
                              code == CloseFrame.GOING_AWAY));
        } finally {
            gc.removeDeadline(d);
        }
    }

    public void onError(WebSocket conn, Exception ex) {
        if (conn == null) {
            LOGGER.log(Level.SEVERE, "Backend exception", ex);
            for (RequestHook h : getAllHooks()) h.onError(null, ex);
            return;
        }
        RequestHook h = assignments.get(conn);
        Datum d = collector.get(conn);
        LOGGER.log(Level.SEVERE, "Exception while handling connection " + d,
                   ex);
        if (h != null) h.onError(d, ex);
    }

    public Iterable<RequestHook> getAllHooks() {
        return Util.concat(hooks, internalHooks);
    }
    public Set<RequestHook> getHooks() {
        return Collections.unmodifiableSet(hooks);
    }
    public Set<RequestHook> getInternalHooks() {
        return Collections.unmodifiableSet(internalHooks);
    }
    public void addHook(RequestHook hook) {
        hooks.add(hook);
    }
    public void addInternalHook(RequestHook hook) {
        internalHooks.add(hook);
    }
    public void removeHook(RequestHook hook) {
        hooks.remove(hook);
        internalHooks.remove(hook);
    }

    public void spawn() {
        gc.start();
        run();
    }

    protected static List<Draft> wrapDrafts(List<Draft> in) {
        List<Draft> out = new ArrayList<Draft>(in.size());
        for (Draft d : in) {
            if (d instanceof DraftWrapper) {
                out.add(d.copyInstance());
            } else {
                out.add(new DraftWrapper(d));
            }
        }
        return out;
    }

    protected static Map<String, String> headerMap(Handshakedata dat) {
        Map<String, String> ret = new LinkedHashMap<String, String>();
        Iterator<String> names = dat.iterateHttpFields();
        while (names.hasNext()) {
            String name = names.next();
            ret.put(name, dat.getFieldValue(name));
        }
        return ret;
    }

}
