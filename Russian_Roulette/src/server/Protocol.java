package server;

public final class Protocol {
    private Protocol() {}

    // handshake
    public static final String HELLO = "HELLO";

    // lobby/room
    public static final String ROOM_STATUS  = "ROOM_STATUS";   // ROOM_STATUS WAITING 1/2 | ROOM_STATUS READY 2/2
    public static final String ROOM_CREATED = "ROOM_CREATED";  // ROOM_CREATED P1=<name> P2=<name>
    public static final String ENTER_ROOM   = "ENTER_ROOM";    // ENTER_ROOM P1=<name> P2=<name>

    // chat
    public static final String CHAT         = "CHAT";          // CHAT <sender>: <message>

    public static String kv(String k, Object v) { return k + "=" + v; }
}
