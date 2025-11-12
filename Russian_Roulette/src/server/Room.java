package server;

import java.util.Random;

public class Room {
    private final ClientHandler p1;
    private final ClientHandler p2;
    private final String n1;
    private final String n2;

    // ==== 게임 상태 ====
    private final Random rnd = new Random();
    private final int[] cyl = new int[6];  // 1=실탄, 0=공탄
    private int idx = 0;                   // 현재 발사칸(0~5)
    private int hp1 = 5, hp2 = 5;          // 체력
    private int turn = 1;                  // 1=P1, 2=P2
    private Target aimP1 = Target.ENEMY;   // 기본 조준
    private Target aimP2 = Target.ENEMY;

    // 남은 장탄 수(표시용)
    private int bulletsLeft = 0; // 남은 실탄 개수
    private int blanksLeft  = 0; // 남은 공탄 개수

    // === [Req 3] Ready 상태 ===
    private boolean p1Ready = false;
    private boolean p2Ready = false;

    private enum Target { SELF, ENEMY }

    public Room(ClientHandler p1, ClientHandler p2, String n1, String n2) {
        this.p1 = p1; this.p2 = p2; this.n1 = n1; this.n2 = n2;
        if (p1 != null) p1.setRoom(this);
        if (p2 != null) p2.setRoom(this);
        randomizeCylinder();
    }

    public void announceCreatedAndReady() {
        broadcast(Protocol.ROOM_CREATED + " P1=" + n1 + " P2=" + n2);
        broadcast(Protocol.ENTER_ROOM   + " P1=" + n1 + " P2=" + n2);
    }

    // [Req 3] 실제 게임 시작 로직
    private void startGame() {
        // === [Req 3-3] GAME_START 신호에 B(Bullets), K(Blanks) 정보 추가 ===
        broadcast(Protocol.GAME_START 
                + " P1=" + n1 
                + " P2=" + n2
                + " B=" + bulletsLeft
                + " K=" + blanksLeft);
        // === [Req 3-3] 끝 ===

        // 초기 상태 알림: RELOAD는 idx(0/6)를 설정하기 위해 여전히 필요
        broadcast(Protocol.RELOAD + " " + idx + "/6 B=" + bulletsLeft + " K=" + blanksLeft);
        broadcast(Protocol.TURN   + " P" + turn);
        // [Req 9] 초기 조준 상태 방송
        broadcast(Protocol.AIM_UPDATE + " WHO=P1 TARGET=ENEMY");
        broadcast(Protocol.AIM_UPDATE + " WHO=P2 TARGET=ENEMY");
    }


    private void randomizeCylinder() {
        int b = 0;
        for (int i = 0; i < 6; i++) {
            cyl[i] = rnd.nextBoolean() ? 1 : 0;
            if (cyl[i] == 1) b++;
        }
        idx = 0;
        bulletsLeft = b;
        blanksLeft  = 6 - b;
    }

    public synchronized void broadcast(String line) {
        if (p1 != null) p1.send(line);
        if (p2 != null) p2.send(line);
    }

    public void broadcastChat(String sender, String message) {
        broadcast(Protocol.CHAT + " " + sender + ": " + message);
    }

    // ==== 클라이언트 명령 처리 ====

    // [Req 3] Ready 처리
    public synchronized void onReady(ClientHandler who) {
        if (who == p1) p1Ready = true;
        else if (who == p2) p2Ready = true;

        broadcast(Protocol.ROOM_STATUS + " P1_READY=" + p1Ready + " P2_READY=" + p2Ready);

        if (p1Ready && p2Ready) {
            startGame();
        }
    }

    // [Req 9] 조준 상태 변경 시 서버에 저장하고 모든 클라에게 방송
    public synchronized void onAim(ClientHandler who, String targetStr) {
        Target t = "SELF".equalsIgnoreCase(targetStr) ? Target.SELF : Target.ENEMY;
        String playerRole = "P_UNKNOWN";
        
        if (who == p1) {
            aimP1 = t;
            playerRole = "P1";
        } else if (who == p2) {
            aimP2 = t;
            playerRole = "P2";
        }
        
        // 변경된 조준 상태를 모두에게 방송
        broadcast(Protocol.AIM_UPDATE + " WHO=" + playerRole + " TARGET=" + targetStr.toUpperCase());
    }

    public synchronized void onFire(ClientHandler who) {
        int shooter = (who == p1) ? 1 : 2;
        if (shooter != turn) return; // 자기 턴이 아니면 무시

        Target t = (shooter == 1) ? aimP1 : aimP2;
        boolean hitSelf  = (t == Target.SELF);

        int result = cyl[idx]; // 1=실탄, 0=공탄
        idx++;

        if (result == 1) { // 대미지 적용 + 남은 실탄 수 감소
            if (shooter == 1) {
                if (hitSelf) hp1--; else hp2--;
            } else {
                if (hitSelf) hp2--; else hp1--;
            }
            bulletsLeft = Math.max(0, bulletsLeft - 1);
        } else {
            blanksLeft = Math.max(0, blanksLeft - 1);
        }

        String r = (result == 1) ? "BULLET" : "BLANK";
        String targetLabel = hitSelf ? "SELF" : "ENEMY";
        broadcast(Protocol.FIRE_RESOLVE
                + " RESULT=" + r
                + " TARGET=" + targetLabel
                + " HP1=" + hp1
                + " HP2=" + hp2
                + " B_LEFT=" + bulletsLeft
                + " K_LEFT=" + blanksLeft
                + " SHOT=" + (idx) + "/6");

        if (hp1 <= 0 || hp2 <= 0) {
            String win = (hp1 <= 0 && hp2 <= 0) ? "DRAW" : (hp1 <= 0 ? "P2" : "P1");
            broadcast(Protocol.GAME_OVER + " WIN=" + win);
            return;
        }

        // === [Req 8] 턴 결정 로직 ===
        boolean turnSwaps = true; // 기본은 턴 교대
        if (result == 0) { // 공탄을 쐈을 때
            if ( (shooter == 1 && hitSelf) || (shooter == 2 && hitSelf) ) {
                // 자신에게 공탄을 쏜 경우
                turnSwaps = false; // 턴 유지
            }
        }
        // === [Req 8] 끝 ===


        // 탄창 소진 → 재장전
        if (idx >= 6) {
            randomizeCylinder();
            broadcast(Protocol.RELOAD + " " + idx + "/6 B=" + bulletsLeft + " K=" + blanksLeft); // 0/6 리셋 + 남은 장탄 수
        }

        // 턴 교대 (필요한 경우) 및 알림
        if (turnSwaps) {
            turn = (turn == 1) ? 2 : 1;
        }
        broadcast(Protocol.TURN + " P" + turn);
    }
}