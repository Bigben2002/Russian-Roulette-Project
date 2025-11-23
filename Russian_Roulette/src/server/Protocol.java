package server;

public final class Protocol {
    // 기본
    public static final String HELLO        = "HELLO";
    public static final String ROOM_STATUS  = "ROOM_STATUS";
    public static final String ROOM_CREATED = "ROOM_CREATED";
    public static final String ENTER_ROOM   = "ENTER_ROOM";
    public static final String CHAT         = "CHAT";
    public static final String READY        = "READY";        // 클라→서버: 준비 완료
    public static final String READY_STATE  = "READY_STATE";  // [NEW] 서버→클라: 준비 상태 업데이트
    public static final String EXIT_ROOM    = "EXIT_ROOM";    // [NEW] 클라→서버: 방 나가기 및 서버→클라: 상대방 퇴장 알림
    public static final String GAME_START   = "GAME_START";   // 서버→클라: 게임 시작

    // 러시안 룰렛
    public static final String AIM          = "AIM";          // 클라→서버: AIM SELF|ENEMY
    public static final String AIM_UPDATE   = "AIM_UPDATE";   // 서버→클라: AIM_UPDATE WHO=P1|P2 TARGET=...
    public static final String FIRE         = "FIRE";         // 클라→서버: FIRE
    public static final String TURN         = "TURN";         // 서버→클라: TURN P1|P2
    public static final String RELOAD       = "RELOAD";       // 서버→클라: RELOAD k/6
    public static final String FIRE_RESOLVE = "FIRE_RESOLVE"; // 서버→클라: FIRE_RESOLVE RESULT=BULLET|BLANK TARGET=SELF|ENEMY HP1=.. HP2=.. SHOT=k/6
    public static final String GAME_OVER    = "GAME_OVER";    // 서버→클라: GAME_OVER WIN=P1|P2|DRAW

    // === 아이템 추가 프로토콜 ===
    public static final String ITEM_UPDATE  = "ITEM_UPDATE";  // 서버→클라: ITEM_UPDATE WHO=P1|P2 ITEMS=H,S,B...
    public static final String USE_ITEM     = "USE_ITEM";     // 클라→서버: USE_ITEM SLOT=1..6
    public static final String PEEK_RESULT  = "PEEK_RESULT";  // 서버→클라: PEEK_RESULT TYPE=BULLET|BLANK
    // === 끝 ===

    private Protocol() {}
}