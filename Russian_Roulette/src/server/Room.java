package server;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
    
    private boolean p1HasBombEffect = false;
    private boolean p2HasBombEffect = false;

    // 남은 장탄 수(표시용)
    private int bulletsLeft = 0; 
    private int blanksLeft  = 0; 

    // === Ready 상태 ===
    private boolean p1Ready = false;
    private boolean p2Ready = false;
    
    // === 아이템 관련 상태 ===
    private String[] p1Items = new String[6]; 
    private String[] p2Items = new String[6];
    // Heal, Search, Bomb
    private static final List<String> ALL_ITEMS = Collections.unmodifiableList(Arrays.asList("H", "S", "B")); 
    // === 끝 ===


    private enum Target { SELF, ENEMY }

    public Room(ClientHandler p1, ClientHandler p2, String n1, String n2) {
        this.p1 = p1; this.p2 = p2; this.n1 = n1; this.n2 = n2;
        if (p1 != null) p1.setRoom(this);
        if (p2 != null) p2.setRoom(this);
        randomizeCylinder();
        
        Arrays.fill(p1Items, "-");
        Arrays.fill(p2Items, "-");
        
        // P2 아이템 초기 지급 로직 포함
        giveItem(p1Items, 3);
        giveItem(p2Items, 3);
    }

    public void announceCreatedAndReady() {
        broadcast(Protocol.ROOM_CREATED + " P1=" + n1 + " P2=" + n2);
        broadcast(Protocol.ENTER_ROOM   + " P1=" + n1 + " P2=" + n2);
    }

    private void startGame() {
        
        String p1ItemsStr = String.join(",", p1Items); // 쉼표(,) 구분자로 통일
        String p2ItemsStr = String.join(",", p2Items);
        
        broadcast(Protocol.GAME_START 
                + " P1=" + n1 
                + " P2=" + n2
                + " B=" + bulletsLeft
                + " K=" + blanksLeft
                + " P1_ITEMS=" + p1ItemsStr 
                + " P2_ITEMS=" + p2ItemsStr);

        broadcast(Protocol.RELOAD + " " + idx + "/6 B=" + bulletsLeft + " K=" + blanksLeft);
        broadcast(Protocol.TURN   + " P" + turn);
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

    public void giveItem(String[] items, int count) {
        int emptySlots = 0;
        for (String item : items) {
            if ("-".equals(item)) emptySlots++;
        }
        
        int provideCount = Math.min(count, emptySlots);
        for (int i = 0; i < provideCount; i++) {
            String newItem = ALL_ITEMS.get(rnd.nextInt(ALL_ITEMS.size()));
            for (int j = 0; j < items.length; j++) {
                if ("-".equals(items[j])) {
                    items[j] = newItem;
                    break;
                }
            }
        }
    }
    
    private void broadcastItemStatus() {
        broadcast(Protocol.ITEM_UPDATE + " WHO=P1 ITEMS=" + String.join(",", p1Items));
        broadcast(Protocol.ITEM_UPDATE + " WHO=P2 ITEMS=" + String.join(",", p2Items));
    }


    public synchronized void broadcast(String line) {
        if (p1 != null) p1.send(line);
        if (p2 != null) p2.send(line);
    }

    public void broadcastChat(String sender, String message) {
        broadcast(Protocol.CHAT + " " + sender + ": " + message);
    }

    // ==== 클라이언트 명령 처리 ====

    public synchronized void onReady(ClientHandler who) {
        if (who == p1) p1Ready = true;
        else if (who == p2) p2Ready = true;

        broadcast(Protocol.READY_STATE + " P1_READY=" + p1Ready + " P2_READY=" + p2Ready);

        if (p1Ready && p2Ready) {
            startGame();
        }
    }

    // [복구] AIM 로직
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
        
        broadcast(Protocol.AIM_UPDATE + " WHO=" + playerRole + " TARGET=" + targetStr.toUpperCase());
    }
    
    // [복구] UseItem 로직 (단순화된 형태)
    public synchronized void onUseItem(ClientHandler who, int slotNum) {
        if (slotNum < 1 || slotNum > 6) return;
        
        String[] myItems = (who == p1) ? p1Items : p2Items;
        int shooter = (who == p1) ? 1 : 2;
        String item = myItems[slotNum - 1];
        
        if ("-".equals(item)) return; 
        
        switch (item) {
            case "H": 
                if (shooter == 1) { 
                    if (hp1 < 5) hp1 = Math.min(hp1 + 1, 5); 
                    else return; 
                } else { 
                    if (hp2 < 5) hp2 = Math.min(hp2 + 1, 5); 
                    else return; 
                }
                break;
                
            case "S": 
                String type = (cyl[idx] == 1) ? "BULLET" : "BLANK";
                who.send(Protocol.PEEK_RESULT + " TYPE=" + type);
                break;
                
            case "B": 
                if (shooter == 1) { p1HasBombEffect = true; }
                else { p2HasBombEffect = true; }
                break;
        }
        
        myItems[slotNum - 1] = "-";
        
        broadcastItemStatus();
        
        if ("H".equals(item)) {
             broadcast(Protocol.FIRE_RESOLVE
                + " RESULT=HEAL"
                + " TARGET=SELF"
                + " HP1=" + hp1 
                + " HP2=" + hp2
                + " B_LEFT=" + bulletsLeft
                + " K_LEFT=" + blanksLeft
                + " SHOT=" + idx + "/6"
                + " DMG=0");
        }
    }


    // [복구] FIRE 로직 (턴 로직 포함)
    public synchronized void onFire(ClientHandler who) {
        int shooter = (who == p1) ? 1 : 2;
        if (shooter != turn) return; // 턴 체크가 여기서 명령을 차단

        Target t = (who == p1) ? aimP1 : aimP2;
        boolean hitSelf  = (t == Target.SELF);

        int result = cyl[idx]; 
        idx++;
        
        int damage = 1;
        boolean hasBombEffect = (shooter == 1) ? p1HasBombEffect : p2HasBombEffect;

        if (result == 1) { 
            if (hasBombEffect) {
                damage = 2; 
            }
            
            if (shooter == 1) {
                if (hitSelf) hp1 -= damage; else hp2 -= damage;
            } else {
                if (hitSelf) hp2 -= damage; else hp1 -= damage;
            }
            bulletsLeft = Math.max(0, bulletsLeft - 1);
        } else { 
            blanksLeft = Math.max(0, blanksLeft - 1);
        }

        if (hasBombEffect) {
            if (shooter == 1) { p1HasBombEffect = false; }
            else { p2HasBombEffect = false; }
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
                + " SHOT=" + (idx) + "/6"
                + " DMG=" + damage);

        if (hp1 <= 0 || hp2 <= 0) {
            String win = (hp1 <= 0 && hp2 <= 0) ? "DRAW" : (hp1 <= 0 ? "P2" : "P1");
            broadcast(Protocol.GAME_OVER + " WIN=" + win);
            return;
        }

        boolean turnSwaps; 
        
        if (result == 1) { // 실탄: 무조건 턴 교대
            turnSwaps = true;
        } else { // 공탄 (result == 0)
            if (hitSelf) { // 공탄 + 자신에게 쏠 경우: 턴 유지
                turnSwaps = false; 
            } else { // 공탄 + 상대방에게 쏠 경우: 턴 교대
                turnSwaps = true;
            }
        } 
        
        int nextTurn = turn; 
        
        if (idx >= 6) {
            randomizeCylinder();
            giveItem(p1Items, 3);
            giveItem(p2Items, 3);
            broadcastItemStatus();
            
            broadcast(Protocol.RELOAD + " " + idx + "/6 B=" + bulletsLeft + " K=" + blanksLeft); 
        }

        if (turnSwaps) {
            nextTurn = (turn == 1) ? 2 : 1;
        }
        
        turn = nextTurn;
        
        broadcast(Protocol.TURN + " P" + turn);
        
        Target nextAim = (turn == 1) ? aimP1 : aimP2;
        String nextAimStr = nextAim.toString().toUpperCase();
        broadcast(Protocol.AIM_UPDATE + " WHO=P" + turn + " TARGET=" + nextAimStr);
    }
}