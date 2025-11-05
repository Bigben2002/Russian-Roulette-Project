package server;

import java.io.IOException;

public class Room {
    private final ClientHandler p1;
    private final ClientHandler p2;
    private final String name1;
    private final String name2;

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
        // 게임방으로 입장 (게임 시작은 안 함 — 내부만 보기)
        broadcast(Protocol.ENTER_ROOM + " "
            + Protocol.kv("P1", name1) + " "
            + Protocol.kv("P2", name2));
    }

    // CHAT 중계
    public void broadcastChat(String sender, String message) throws IOException {
        broadcast(Protocol.CHAT + " " + sender + ": " + message);
    }
}
