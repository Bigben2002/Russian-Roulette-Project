package server;

public class Room {
    private final ClientHandler p1;
    private final ClientHandler p2;
    private final String n1;
    private final String n2;

    public Room(ClientHandler p1, ClientHandler p2, String n1, String n2) {
        this.p1 = p1; this.p2 = p2; this.n1 = n1; this.n2 = n2;
        if (p1 != null) p1.setRoom(this);
        if (p2 != null) p2.setRoom(this);
    }

    public void announceCreatedAndReady() {
        String created = Protocol.ROOM_CREATED + " P1=" + n1 + " P2=" + n2;
        String enter   = Protocol.ENTER_ROOM   + " P1=" + n1 + " P2=" + n2;
        broadcast(created);
        broadcast(enter);
    }

    public synchronized void broadcast(String line) {
        if (p1 != null) p1.send(line);
        if (p2 != null) p2.send(line);
    }

    public void broadcastChat(String sender, String message) {
        broadcast(Protocol.CHAT + " " + sender + ": " + message);
    }
}
