package server;

import java.io.IOException;

public class Room {
    private final ClientHandler p1;
    private final ClientHandler p2;
    private String name1;
    private String name2;

    public Room(ClientHandler p1, ClientHandler p2, String name1, String name2) {
        this.p1 = p1;
        this.p2 = p2;
        this.name1 = name1;
        this.name2 = name2;
    }

    public void broadcast(String line) throws IOException {
        p1.send(line);
        p2.send(line);
    }

    public void announceCreatedAndReady() throws IOException {
        broadcast(Protocol.ROOM_CREATED + " "
                + Protocol.kv("P1", name1) + " "
                + Protocol.kv("P2", name2));
        broadcast(Protocol.ROOM_STATUS + " READY 2/2");
    }
}
