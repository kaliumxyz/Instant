package net.instant.proto;

import java.util.LinkedHashSet;
import java.util.Set;
import net.instant.api.ClientConnection;
import net.instant.api.MessageContents;
import net.instant.api.Room;
import net.instant.api.RoomGroup;
import net.instant.util.UniqueCounter;

public class RoomDistributor implements Room {

    private final RoomGroup parent;
    private final String name;
    private final Set<ClientConnection> clients;

    public RoomDistributor(RoomGroup parent, String name) {
        this.parent = parent;
        this.name = name;
        this.clients = new LinkedHashSet<ClientConnection>();
    }

    public String getName() {
        return name;
    }

    public synchronized Set<ClientConnection> getClients() {
        return new LinkedHashSet<ClientConnection>(clients);
    }

    public void sendUnicast(ClientConnection client, MessageContents msg) {
        client.getConnection().send(msg.toString());
    }

    public void sendBroadcast(MessageContents msg) {
        if (name == null)
            throw new UnsupportedOperationException(
                "Trying to broadcast outside any room");
        String s = msg.toString();
        synchronized (this) {
            for (ClientConnection conn : clients)
                conn.getConnection().send(s);
        }
    }

    public RoomGroup getGroup() {
        return parent;
    }

    public MessageContents makeMessage(boolean makeID, String type) {
        return new MessageData(((makeID) ?
            UniqueCounter.INSTANCE.getString() : null), type);
    }

    public synchronized void add(ClientConnection client) {
        clients.add(client);
    }
    public synchronized void remove(ClientConnection client) {
        clients.remove(client);
    }

}
