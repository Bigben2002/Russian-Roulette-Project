package server;

public final class Protocol {
    // 기본
    public static final String HELLO        = "HELLO";
    public static final String ROOM_STATUS  = "ROOM_STATUS";
    public static final String ROOM_CREATED = "ROOM_CREATED";
    public static final String ENTER_ROOM   = "ENTER_ROOM";
    public static final String CHAT         = "CHAT";
    public static final String READY        = "READY";        // [Req 3] 클라→서버: 준비 완료
    public static final String GAME_START   = "GAME_START";   // [Req 3] 서버→클라: 게임 시작

    // 러시안 룰렛
    public static final String AIM          = "AIM";          // 클라→서버: AIM SELF|ENEMY
    public static final String AIM_UPDATE   = "AIM_UPDATE";   // [Req 9] 서버→클라: AIM_UPDATE WHO=P1|P2 TARGET=...
    public static final String FIRE         = "FIRE";         // 클라→서버: FIRE
    public static final String TURN         = "TURN";         // 서버→클라: TURN P1|P2
    public static final String RELOAD       = "RELOAD";       // 서버→클라: RELOAD k/6
    public static final String FIRE_RESOLVE = "FIRE_RESOLVE"; // 서버→클라: FIRE_RESOLVE RESULT=BULLET|BLANK TARGET=SELF|ENEMY HP1=.. HP2=.. SHOT=k/6
    public static final String GAME_OVER    = "GAME_OVER";    // 서버→클라: GAME_OVER WIN=P1|P2|DRAW

    private Protocol() {}
}