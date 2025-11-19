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
    
    // [추가] 폭탄 효과 상태 변수 (사용되었는지 추적)
    private boolean p1HasBombEffect = false;
    private boolean p2HasBombEffect = false;

    // 남은 장탄 수(표시용)
    private int bulletsLeft = 0; // 남은 실탄 개수
    private int blanksLeft  = 0; // 남은 공탄 개수

    // === [Req 3] Ready 상태 ===
    private boolean p1Ready = false;
    private boolean p2Ready = false;
    
    // === 아이템 관련 상태 ===
    private String[] p1Items = new String[6]; // H(Heal), S(Search), B(Bomb)
    private String[] p2Items = new String[6];
    private static final List<String> ALL_ITEMS = Collections.unmodifiableList(Arrays.asList("H", "S", "B"));
    // === 끝 ===


    private enum Target { SELF, ENEMY }

    public Room(ClientHandler p1, ClientHandler p2, String n1, String n2) {
        this.p1 = p1; this.p2 = p2; this.n1 = n1; this.n2 = n2;
        if (p1 != null) p1.setRoom(this);
        if (p2 != null) p2.setRoom(this);
        randomizeCylinder();
        // 배열 초기화
        Arrays.fill(p1Items, "-");
        Arrays.fill(p2Items, "-");
        
        // 게임 시작 아이템 사전 지급 (서버 메모리 저장)
        giveItem(p1Items, 3);
        giveItem(p2Items, 3);
    }

    public void announceCreatedAndReady() {
        broadcast(Protocol.ROOM_CREATED + " P1=" + n1 + " P2=" + n2);
        broadcast(Protocol.ENTER_ROOM   + " P1=" + n1 + " P2=" + n2);
        
        // 초기 아이템 상태 방송은 startGame()에서 처리합니다.
    }

    private void startGame() {
        
        // [핵심 수정 1] 초기 아이템 상태를 GAME_START 메시지에 직접 첨부합니다.
        String p1ItemsStr = String.join(".", p1Items);
        String p2ItemsStr = String.join(".", p2Items);
        
        broadcast(Protocol.GAME_START 
                + " P1=" + n1 
                + " P2=" + n2
                + " B=" + bulletsLeft
                + " K=" + blanksLeft
                + " P1_ITEMS=" + p1ItemsStr // <--- P1 아이템 목록 추가
                + " P2_ITEMS=" + p2ItemsStr); // <--- P2 아이템 목록 추가

        // 초기 상태 알림
        broadcast(Protocol.RELOAD + " " + idx + "/6 B=" + bulletsLeft + " K=" + blanksLeft);
        broadcast(Protocol.TURN   + " P" + turn);
        // [Req 9] 초기 조준 상태 방송
        broadcast(Protocol.AIM_UPDATE + " WHO=P1 TARGET=ENEMY");
        broadcast(Protocol.AIM_UPDATE + " WHO=P2 TARGET=ENEMY");
        
        // [핵심 수정 2] 턴 메시지 후 2차 방송 (클라이언트 UI 준비 완료 보장)
        broadcastItemStatus();
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
        broadcast(Protocol.ITEM_UPDATE + " WHO=P1 ITEMS=" + String.join(".", p1Items));
        broadcast(Protocol.ITEM_UPDATE + " WHO=P2 ITEMS=" + String.join(".", p2Items));
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

        broadcast(Protocol.ROOM_STATUS + " P1_READY=" + p1Ready + " P2_READY=" + p2Ready);

        if (p1Ready && p2Ready) {
            startGame();
        }
    }

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
    
    public synchronized void onUseItem(ClientHandler who, int slotNum) {
        if (slotNum < 1 || slotNum > 6) return;
        
        String[] myItems = (who == p1) ? p1Items : p2Items;
        int shooter = (who == p1) ? 1 : 2;
        String item = myItems[slotNum - 1];
        
        if ("-".equals(item)) return; 
        
        // === [추가] 아이템 사용 로그 ===
        String playerName = (who == p1) ? n1 : n2;
        System.out.println("ITEM USED: [" + playerName + "] P" + shooter + 
                           " used item [" + item + "] from slot " + slotNum);
        // === [추가 끝] ===
        
        // 아이템 사용 및 효과 적용
        switch (item) {
            case "H": // Heal: life를 1칸 회복
                // 서버 측에서 HP가 최대면 사용 거부 (클라이언트 검증 우회 대비)
                if (shooter == 1) { 
                    if (hp1 < 5) hp1 = Math.min(hp1 + 1, 5); 
                    else return; 
                } else { 
                    if (hp2 < 5) hp2 = Math.min(hp2 + 1, 5); 
                    else return; 
                }
                break;
                
            case "S": // Search(돋보기): 다음 발사할 총알 확인
                String type = (cyl[idx] == 1) ? "BULLET" : "BLANK";
                who.send(Protocol.PEEK_RESULT + " TYPE=" + type);
                break;
                
            case "B": // Bomb(폭탄): 사용 즉시 효과 상태 플래그 설정
                if (shooter == 1) { p1HasBombEffect = true; }
                else { p2HasBombEffect = true; }
                
                // === [디버그 추가] ===
                System.out.println("BOMB USED: P" + shooter + " Bomb Effect set to TRUE.");
                // === [디버그 끝] ===
                break;
        }
        
        // [핵심 수정] 아이템 소모: 모든 아이템은 사용 후 인벤토리에서 제거됩니다.
        // Search(S)에 대한 예외 처리는 필요 없습니다.
        myItems[slotNum - 1] = "-";
        
        // 상태 업데이트 방송
        broadcastItemStatus();
        
        // HP 변화가 있는 경우, FIRE_RESOLVE를 통해 HP 동기화
        if ("H".equals(item)) {
             broadcast(Protocol.FIRE_RESOLVE
                + " RESULT=HEAL"
                + " TARGET=SELF"
                + " HP1=" + hp1 // 증가된 HP 값 전송
                + " HP2=" + hp2
                + " B_LEFT=" + bulletsLeft
                + " K_LEFT=" + blanksLeft
                + " SHOT=" + idx + "/6"
                + " DMG=0"); // Heal은 데미지 0으로 전송
        }
    }


    public synchronized void onFire(ClientHandler who) {
        int shooter = (who == p1) ? 1 : 2;
        if (shooter != turn) return; 

        Target t = (shooter == 1) ? aimP1 : aimP2;
        boolean hitSelf  = (t == Target.SELF);

        int result = cyl[idx]; // 1=실탄, 0=공탄
        idx++;
        
        // === [Bomb 효과 확인] ===
        int damage = 1;
        boolean hasBombEffect = (shooter == 1) ? p1HasBombEffect : p2HasBombEffect;

        // === [디버그 추가] ===
        System.out.println("FIRE CHECK: P" + shooter + 
                           " | Has Bomb Effect: " + hasBombEffect + 
                           " | Is Bullet: " + (result == 1));
        // === [디버그 끝] ===
        
        if (result == 1) { // 실탄
            if (hasBombEffect) {
                damage = 2; // [핵심] 폭탄 데미지 2 적용
            }
            
            // 데미지 적용 + 남은 실탄 수 감소
            if (shooter == 1) {
                if (hitSelf) hp1 -= damage; else hp2 -= damage;
            } else {
                if (hitSelf) hp2 -= damage; else hp1 -= damage;
            }
            bulletsLeft = Math.max(0, bulletsLeft - 1);
        } else { // 공탄
            blanksLeft = Math.max(0, blanksLeft - 1);
        }

        // Bomb 효과 소멸 로직: 발사 후 무조건 소멸
        if (hasBombEffect) {
            if (shooter == 1) { p1HasBombEffect = false; }
            else { p2HasBombEffect = false; }
            
            // === [디버그 추가] ===
            System.out.println("BOMB EFFECT EXPIRED for P" + shooter);
            // === [디버그 끝] ===
        }

        String r = (result == 1) ? "BULLET" : "BLANK";
        String targetLabel = hitSelf ? "SELF" : "ENEMY";
        
        // FIRE_RESOLVE 메시지 발송
        broadcast(Protocol.FIRE_RESOLVE
                + " RESULT=" + r
                + " TARGET=" + targetLabel
                + " HP1=" + hp1 // 감소된 HP 값 전송 (3)
                + " HP2=" + hp2
                + " B_LEFT=" + bulletsLeft
                + " K_LEFT=" + blanksLeft
                + " SHOT=" + (idx) + "/6"
                + " DMG=" + damage); // DMG=2 전송


        if (hp1 <= 0 || hp2 <= 0) {
            String win = (hp1 <= 0 && hp2 <= 0) ? "DRAW" : (hp1 <= 0 ? "P2" : "P1");
            broadcast(Protocol.GAME_OVER + " WIN=" + win);
            return;
        }

        // === 턴 결정 로직 ===
        boolean turnSwaps; 
        
        if (result == 1) { // 실탄: 무조건 턴 교대
            turnSwaps = true;
        } else { // 공탄 (result == 0)
            if (hitSelf) { 
                // 공탄 + 자신에게 쏠 경우: 턴 유지
                turnSwaps = false; 
            } else {
                // 공탄 + 상대방에게 쏠 경우: 턴 교대
                turnSwaps = true;
            }
        } 
        
        int nextTurn = turn; // 현재 턴 저장
        
        // 탄창 소진 → 재장전
        if (idx >= 6) {
            randomizeCylinder();
            // === 재장전 시 아이템 3개 제공 ===
            giveItem(p1Items, 3);
            giveItem(p2Items, 3);
            broadcastItemStatus();
            // === 끝 ===
            
            broadcast(Protocol.RELOAD + " " + idx + "/6 B=" + bulletsLeft + " K=" + blanksLeft); 
        }

        // 턴 교대 (필요한 경우) 및 알림
        if (turnSwaps) {
            nextTurn = (turn == 1) ? 2 : 1;
        }
        
        // 턴을 다음 턴으로 설정
        turn = nextTurn;
        
        // [핵심 추가] 다음 턴을 알림
        broadcast(Protocol.TURN + " P" + turn);
        
        // [핵심 추가] 턴 신호를 보낸 직후, 새로 턴을 받은 플레이어의 조준 상태를 다시 방송하여 총구 회전 동기화
        Target nextAim = (turn == 1) ? aimP1 : aimP2;
        String nextAimStr = nextAim.toString().toUpperCase();
        broadcast(Protocol.AIM_UPDATE + " WHO=P" + turn + " TARGET=" + nextAimStr);
    }
}