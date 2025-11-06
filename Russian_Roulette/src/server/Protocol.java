package server;

public final class Protocol {
    // 서버→클라
    public static final String HELLO        = "HELLO";        // 서버가 이름 요청
    public static final String ROOM_STATUS  = "ROOM_STATUS";  // WAITING/READY 상태
    public static final String ROOM_CREATED = "ROOM_CREATED"; // 방 생성 알림
    public static final String ENTER_ROOM   = "ENTER_ROOM";   // 게임방 진입 신호

    // 공통
    public static final String CHAT         = "CHAT";         // CHAT <sender>: <msg>

    private Protocol() {}
}
