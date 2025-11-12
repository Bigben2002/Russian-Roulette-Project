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
        // 초기 상태 알림: 남은 장탄 수 포함
        broadcast(Protocol.RELOAD + " " + idx + "/6 B=" + bulletsLeft + " K=" + blanksLeft);
        broadcast(Protocol.TURN   + " P" + turn);
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
    public synchronized void onAim(ClientHandler who, String targetStr) {
        Target t = "SELF".equalsIgnoreCase(targetStr) ? Target.SELF : Target.ENEMY;
        if (who == p1) aimP1 = t;
        else if (who == p2) aimP2 = t;
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

        // 탄창 소진 → 재장전
        if (idx >= 6) {
            randomizeCylinder();
            broadcast(Protocol.RELOAD + " " + idx + "/6 B=" + bulletsLeft + " K=" + blanksLeft); // 0/6 리셋 + 남은 장탄 수
        }

        // 턴 교대 후 알림
        turn = (turn == 1) ? 2 : 1;
        broadcast(Protocol.TURN + " P" + turn);
    }
}
