package net.instant;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshakeBuilder;

public class WebSocketHook extends HookAdapter {

    private final List<Pattern> whitelist;

    public WebSocketHook() {
        whitelist = new ArrayList<Pattern>();
    }

    public void whitelist(Pattern p) {
        whitelist.add(p);
    }
    public void whitelist(String p) {
        whitelist.add(Pattern.compile(p));
    }

    public boolean allowUnassigned() {
        return false;
    }

    public Boolean verifyWebSocket(ClientHandshake handshakedata,
                                   boolean guess) {
        return Util.matchWhitelist(handshakedata.getResourceDescriptor(),
                                   whitelist);
    }

    public void postProcessRequest(InstantWebSocketServer parent,
                                   InformationCollector.Datum info,
                                   ClientHandshake request,
                                   ServerHandshakeBuilder response,
                                   Handshakedata eff_resp) {
        if (parent.getEffectiveDraft(info) instanceof Draft_Raw) return;
        info.setResponseInfo(response, (short) 101, "Switching Protocols",
                             -1);
        response.put("Content-Type", "application/x-websocket");
        parent.assign(info, this);
    }

}
