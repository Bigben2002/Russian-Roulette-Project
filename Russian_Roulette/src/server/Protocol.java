package server;

public final class Protocol {
    private Protocol() {}

    // handshake
    public static final String HELLO = "HELLO";

    // room / lobby
    public static final String ROOM_STATUS  = "ROOM_STATUS";  // ROOM_STATUS WAITING 1/2  |  ROOM_STATUS READY 2/2
    public static final String ROOM_CREATED = "ROOM_CREATED"; // ROOM_CREATED P1=<name> P2=<name>

    // future use (게임 시작 전이라 여기까지만)
    public static String kv(String k, Object v) { return k + "=" + v; }
}

