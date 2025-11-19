package client;

import server.Protocol;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.function.Consumer;

public class GameRoomFrame extends JFrame {
    private final String p1Name;
    private final String p2Name;
    private final String myName;
    private final NetworkClient net;

    private ChatDialog chatDialog;
    private final StringBuilder chatLog = new StringBuilder();
    private volatile boolean enteredRoom = true;

    private final RoomCanvas canvas;

    private String myRole;               // "P1" or "P2"
    private String currentAim = "ENEMY"; // SELF | ENEMY (내 조준 상태)

    // ===== 서버 방송 상태(표시용) =====
    private int hp1 = 5;
    private int hp2 = 5;
    private static final int MAX_HP = 5;
    private String currentTurn = "P1"; // "P1" | "P2"
    private int shotIndex = 0;         // 0~6
    private int bulletsLeft = 0;       // 남은 실탄 수
    private int blanksLeft  = 0;       // 남은 공탄 수
    private String gameOverBanner = null;

    // === [Req 9] 플레이어별 조준 상태 ===
    private String p1Aim = "ENEMY";
    private String p2Aim = "ENEMY";
    // === [Req 9] 끝 ===
    
    // === [Item] 아이템 가방 상태 ===
    private String[] myItems = new String[6];
    private String[] enemyItems = new String[6];
    private String peekResult = null; // 돋보기(Search) 사용 결과
    private String bombStatus = null; // 폭탄(Bomb) 사용 상태
    // === [Item] 끝 ===


    // ===== 총 회전(애니메이션) =====
    private double currentAngleRad = 0.0;      // 현재 각도
    private double targetAngleRad  = 0.0;      // 목표 각도
    private final Timer rotTimer;              // 부드러운 회전용 타이머
    private final double ROT_STEP = Math.toRadians(12); // 틱당 12도

    // === [핵심 수정] 생성자: 아이템 초기 목록 파라미터 추가 ===
    public GameRoomFrame(String p1Name, String p2Name, String myName, NetworkClient net, 
                         int initialBullets, int initialBlanks,
                         String p1InitialItems, String p2InitialItems) { // <--- 아이템 파라미터 추가
        super("Game Room");
        this.p1Name = p1Name; this.p2Name = p2Name; this.myName = myName; this.net = net;
        this.myRole = myName.equals(p1Name) ? "P1" : "P2";

        this.bulletsLeft = initialBullets;
        this.blanksLeft = initialBlanks;
        
        // 아이템 배열 초기화 대신, 파싱하여 즉시 아이템 상태를 설정
        if ("P1".equals(myRole)) {
            setItemsFromStrings(p1InitialItems, this.myItems);
            setItemsFromStrings(p2InitialItems, this.enemyItems);
        } else {
            setItemsFromStrings(p2InitialItems, this.myItems);
            setItemsFromStrings(p1InitialItems, this.enemyItems);
        }


        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(960, 640);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // 중앙 캔버스
        canvas = new RoomCanvas();
        add(canvas, BorderLayout.CENTER);

        // 상단 바
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        JLabel youLabel = new JLabel("You: " + myRole + " (" + myName + ")");
        topBar.add(youLabel, BorderLayout.WEST);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton keyBtn  = new JButton("조작키");
        keyBtn.setFocusable(false);
        JButton chatBtn = new JButton("Chat");
        chatBtn.setFocusable(false);
        
        keyBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) { showKeyHelp(); }
        });
        chatBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) { ensureChatDialog(); }
        });
        rightButtons.add(keyBtn);
        rightButtons.add(chatBtn);
        rightButtons.setOpaque(false);
        topBar.add(rightButtons, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // 회전 타이머(애니메이션)
        rotTimer = new Timer(16, new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                double diff = targetAngleRad - currentAngleRad;
                if (Math.abs(diff) <= ROT_STEP) {
                    currentAngleRad = targetAngleRad;
                    rotTimer.stop();
                } else {
                    currentAngleRad += Math.signum(diff) * ROT_STEP;
                }
                canvas.repaint();
            }
        });
        rotTimer.setRepeats(true);

        setupKeyBindings();
        net.setOnLine(getLineConsumer());
    }
    
    // === [추가] 문자열을 배열로 변환하는 헬퍼 메소드 ===
    private void setItemsFromStrings(String itemsStr, String[] targetArray) {
        if (itemsStr == null || itemsStr.isEmpty()) {
            Arrays.fill(targetArray, "-");
            return;
        }
        String[] items = itemsStr.split("\\.");
        System.arraycopy(items, 0, targetArray, 0, Math.min(items.length, targetArray.length));
        // 남은 공간이 있다면 '-'로 채웁니다.
        if (items.length < targetArray.length) {
            Arrays.fill(targetArray, items.length, targetArray.length, "-");
        }
    }
    // === [추가 끝] ===


    private void showKeyHelp() {
        JOptionPane.showMessageDialog(this,
                "↑: 조준 이동 (ENEMY)\n" +
                "↓: 조준 이동 (SELF)\n" +
                "SPACE: 발사\n" +
                "1-6: 아이템 사용",
                "조작키", JOptionPane.INFORMATION_MESSAGE);
    }

    private void ensureChatDialog() {
        if (chatDialog == null) chatDialog = new ChatDialog(this);
        chatDialog.setVisible(true);
    }

    public Consumer<String> getLineConsumer() {
        return new Consumer<String>() {
            @Override public void accept(String line) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override public void run() { handleServerLine(line); }
                });
            }
        };
    }

    private void handleServerLine(String line) {
        if (line == null) return;
        
        if (line.startsWith(Protocol.ENTER_ROOM)) {
            enteredRoom = true;
            return;
        }

        if (line.startsWith(Protocol.TURN + " ")) {
            String who = line.substring((Protocol.TURN + " ").length()).trim();
            if (who.equals("P1") || who.equals("P2")) currentTurn = who;
            updateGunAngleForCurrentTurn(); 
            canvas.repaint();
            return;
        }
        
        if (line.startsWith(Protocol.AIM_UPDATE + " ")) {
            String who = "P_UNKNOWN";
            String target = "ENEMY";
            String[] sp = line.split("\\s+");
            for (int i = 0; i < sp.length; i++) {
                if (sp[i].startsWith("WHO=")) who = sp[i].substring(4);
                else if (sp[i].startsWith("TARGET=")) target = sp[i].substring(7);
            }
            
            if ("P1".equals(who)) p1Aim = target;
            else if ("P2".equals(who)) p2Aim = target;

            updateGunAngleForCurrentTurn(); 
            canvas.repaint();
            return;
        }


        if (line.startsWith(Protocol.RELOAD + " ")) {
            parseReload(line);
            canvas.repaint();
            return;
        }

        if (line.startsWith(Protocol.FIRE_RESOLVE + " ")) {
            // FIRE_RESOLVE 메시지를 받으면 돋보기/폭탄 상태를 초기화
            peekResult = null; // Fire 이후 돋보기 결과는 지워야 함 (폭탄 상태처럼)
            bombStatus = null; // 폭탄 상태 확실히 초기화
            parseFireResolve(line);
            canvas.repaint();
            return;
        }

        if (line.startsWith(Protocol.GAME_OVER + " ")) {
            String win = "UNKNOWN";
            String[] sp = line.split("\\s+");
            for (int i = 0; i < sp.length; i++) {
                if (sp[i].startsWith("WIN=")) { win = sp[i].substring(4); break; }
            }
            gameOverBanner = "GAME OVER - WIN: " + win;
            canvas.repaint();
            return;
        }
        
        // === [Item] 아이템 업데이트 (재장전, 아이템 사용 후 서버로부터의 일반적인 업데이트) ===
        if (line.startsWith(Protocol.ITEM_UPDATE + " ")) {
            parseItemUpdate(line);
            canvas.repaint();
            return;
        }
        
        // === [핵심 수정] 돋보기 결과 처리 ===
        if (line.startsWith(Protocol.PEEK_RESULT + " ")) {
            String result = parseKV(line, "TYPE");
            // peekResult 변수에 값을 저장하고 UI를 갱신합니다.
            peekResult = "총알 확인: " + ("BULLET".equals(result) ? "실탄" : "공탄"); 
            canvas.repaint(); // UI 갱신 요청
            return;
        }
    }

    private void parseReload(String line) {
        try {
            int slash = line.indexOf('/');
            int spaceAfter = line.indexOf(' ', slash);
            if (slash > 0 && spaceAfter > slash) {
                String left = line.substring(Protocol.RELOAD.length() + 1, slash).trim();
                shotIndex = Integer.parseInt(left); // 보통 0
            }
            String[] sp = line.split("\\s+");
            for (int i = 0; i < sp.length; i++) {
                if (sp[i].startsWith("B=")) bulletsLeft = parseIntSafe(sp[i].substring(2), bulletsLeft);
                else if (sp[i].startsWith("K=")) blanksLeft = parseIntSafe(sp[i].substring(2), blanksLeft);
            }
        } catch (Exception ignore) {}
    }

    private void parseFireResolve(String line) {
        String[] sp = line.split("\\s+");
        int damageReceived = 1; 
        
        for (int i = 0; i < sp.length; i++) {
            if (sp[i].startsWith("HP1=")) hp1 = parseIntSafe(sp[i].substring(4), hp1);
            else if (sp[i].startsWith("HP2=")) hp2 = parseIntSafe(sp[i].substring(4), hp2);
            else if (sp[i].startsWith("B_LEFT=")) bulletsLeft = parseIntSafe(sp[i].substring(7), bulletsLeft);
            else if (sp[i].startsWith("K_LEFT=")) blanksLeft = parseIntSafe(sp[i].substring(7), blanksLeft);
            else if (sp[i].startsWith("SHOT=")) {
                int slash = sp[i].indexOf('/');
                if (slash > 5) {
                    String left = sp[i].substring(5, slash);
                    shotIndex = parseIntSafe(left, shotIndex);
                }
            }
            else if (sp[i].startsWith("DMG=")) {
                damageReceived = parseIntSafe(sp[i].substring(4), 1);
            }
        }
        
        // 디버그 로그 유지
        System.out.println("🔥 FIRE_RESOLVE - Applied Damage: " + damageReceived + 
                           " | HP1: " + hp1 + " | HP2: " + hp2);
    }
    
    private void parseItemUpdate(String line) {
        String who = parseKV(line, "WHO");
        String itemsStr = parseKV(line, "ITEMS");
        if (itemsStr == null) return;
        
        if ("P1".equals(who)) {
            if ("P1".equals(myRole)) setItemsFromStrings(itemsStr, myItems);
            else setItemsFromStrings(itemsStr, enemyItems);
        } else if ("P2".equals(who)) {
            if ("P2".equals(myRole)) setItemsFromStrings(itemsStr, myItems);
            else setItemsFromStrings(itemsStr, enemyItems);
        }
    }


    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return def; }
    }
    
    private String parseKV(String line, String key) {
        String[] sp = line.split("\\s+");
        for (String tok : sp) {
            if (tok.startsWith(key + "=")) return tok.substring((key + "=").length());
        }
        return null;
    }

    private void setupKeyBindings() {
        JComponent c = getRootPane();
        // AIM/FIRE 액션은 WHEN_IN_FOCUSED_WINDOW에서 처리
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("DOWN"),  "AIM_SELF");
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("UP"), "AIM_ENEMY");
        
        // 스페이스바와 아이템 키는 Key Released 시점에만 동작하도록 수정하여 반복 호출 방지
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true), "FIRE_ACTION");
        
        // === [Item] 키패드 1-6 아이템 사용 키 바인딩 (Key Released) ===
        for (int i = 1; i <= 6; i++) {
            c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_0 + i, 0, true), // true = Key Released
                "USE_ITEM_" + i
            );
            int slot = i;
            c.getActionMap().put("USE_ITEM_" + i, new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                    tryUseItem(slot); 
                }
            });
        }
        // === [Item] 끝 ===
        
        c.getActionMap().put("AIM_SELF", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!myRole.equals(currentTurn)) return;
                currentAim = "SELF";
                net.send(Protocol.AIM + " SELF");
            }
        });
        c.getActionMap().put("AIM_ENEMY", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!myRole.equals(currentTurn)) return;
                currentAim = "ENEMY";
                net.send(Protocol.AIM + " ENEMY");
            }
        });
        
        c.getActionMap().put("FIRE_ACTION", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { tryFire(); }
        });
    }

    private void tryUseItem(int slot) {
        
        if (!myRole.equals(currentTurn) || gameOverBanner != null) return; 

        int myHp = "P1".equals(myRole) ? hp1 : hp2;
        String item = myItems[slot - 1];
        
        // 배열 경계 및 아이템 유무 체크
        if (slot < 1 || slot > myItems.length || "-".equals(item)) {
             return;
        }
        
        // [수정] Heal 아이템 사용 전 최대 HP 체크 (클라이언트 측 검증)
        if ("H".equals(item)) {
            if (myHp >= MAX_HP) {
                 JOptionPane.showMessageDialog(this, "HP가 가득 차서 Heal을 사용할 수 없습니다.");
                 return;
            }
        }
        
        // [핵심 수정] 클라이언트 UI에서 돋보기를 포함한 모든 아이템 즉시 제거
        // 돋보기 (S)도 사용 즉시 사라지게 합니다.
        myItems[slot - 1] = "-"; 
             
        if ("B".equals(item)) {
           bombStatus = "강화!!! (다음 발사 실탄 데미지 2배)";
        }
        
        canvas.repaint(); // UI 즉시 업데이트
        
        // [핵심 수정] 서버로 명령 전송
        net.send(Protocol.USE_ITEM + " SLOT=" + slot);
    }

    private void updateGunAngleForCurrentTurn() {
        String targetAim;
        // 총구는 현재 턴인 플레이어가 조준한 곳으로 향합니다.
        if ("P1".equals(currentTurn)) {
            targetAim = p1Aim;
        } else {
            targetAim = p2Aim;
        }
        
        targetAngleRad = "ENEMY".equals(targetAim) ? -Math.PI/2 : Math.PI/2;
        
        // [핵심 복원] 목표 각도만 설정하고 타이머는 무조건 시작합니다.
        if (!rotTimer.isRunning()) rotTimer.start();
        
        canvas.repaint();
    }

    private void tryFire() {
        if (!myRole.equals(currentTurn)) { return; }
        net.send(Protocol.FIRE);
    }
    
    // ====== 캔버스(배경/플레이어/총/표시) ======
    class RoomCanvas extends JPanel {
        private final Image bg;
        private final Image p1Img;
        private final Image p2Img;
        private final Image gunImg;
        private final Image lifeImg;
        private final Image healImg;
        private final Image searchImg;
        private final Image bombImg;


        RoomCanvas() {
            // *** 이미지 로드 경로/파일명 복구 ***
            ImageIcon bgIcon   = ImageLoader.load("images/room_bg.png");
            ImageIcon p1Icon   = ImageLoader.load("images/player1.png");
            ImageIcon p2Icon   = ImageLoader.load("images/player2.png");
            ImageIcon gunIcon  = ImageLoader.load("images/gun.png");
            ImageIcon lifeIcon = ImageLoader.load("images/life.png");
            
            // === [Item] 아이템 이미지 로드 ===
            ImageIcon healIcon   = ImageLoader.load("images/Heal.png");
            ImageIcon searchIcon = ImageLoader.load("images/Search.png");
            ImageIcon bombIcon   = ImageLoader.load("images/bomb.png");
            // === [Item] 끝 ===

            bg     = (bgIcon == null)  ? null : bgIcon.getImage();
            p1Img  = (p1Icon == null)  ? null : p1Icon.getImage();
            p2Img  = (p2Icon == null)  ? null : p2Icon.getImage();
            gunImg = (gunIcon == null) ? null : gunIcon.getImage();
            lifeImg= (lifeIcon == null)? null : lifeIcon.getImage();
            
            // === [Item] 아이템 이미지 변수 할당 ===
            healImg  = (healIcon == null) ? null : healIcon.getImage();
            searchImg = (searchIcon == null) ? null : searchIcon.getImage();
            bombImg  = (bombIcon == null) ? null : bombIcon.getImage();
            // === [Item] 끝 ===
        }


        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth(), h = getHeight();

            // 배경
            if (bg != null) g.drawImage(bg, 0, 0, w, h, this);
            else {
                g.setColor(new Color(40, 40, 40));
                g.fillRect(0, 0, w, h);
            }

            // 크기 & 여백
            final int imgW = 180, imgH = 180;
            final int marginTop = 40;
            final int marginBottom = 40;
            int centerX = (w - imgW) / 2;

            // === [Req 2] 내/상대 위치 고정 ===
            Image myImg, enemyImg;
            int myX, myY, enemyX, enemyY;
            
            int pBottomX = centerX;
            int pBottomY = h - imgH - marginBottom;
            int pTopX = centerX;
            int pTopY = marginTop;

            if ("P1".equals(myRole)) {
                myImg = p1Img;     enemyImg = p2Img;
                myX = pBottomX;    myY = pBottomY;
                enemyX = pTopX;    enemyY = pTopY;
            } else { // myRole is P2
                myImg = p2Img;     enemyImg = p1Img;
                myX = pBottomX;    myY = pBottomY;
                enemyX = pTopX;    enemyY = pTopY;
            }
            
            // Draw Enemy: 위
            if (enemyImg != null) g.drawImage(enemyImg, enemyX, enemyY, imgW, imgH, this);
            // Draw Me: 아래
            if (myImg != null) g.drawImage(myImg, myX, myY, imgW, imgH, this);
            // === [Req 2] 끝 ===


            // ===== 총(회전) =====
            final int gunW = 420, gunH = 420;
            int gunX = (w - gunW) / 2;
            int gunY = (h - gunH) / 2;

            if (gunImg != null) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int cx = gunX + gunW / 2;
                int cy = gunY + gunH / 2;
                g2.rotate(currentAngleRad, cx, cy);
                g2.drawImage(gunImg, gunX, gunY, gunW, gunH, this);
                g2.dispose();
            }

            // HUD(턴/HP/탄/샷/아이템)
            drawHUD(g, w, h, myX, myY, enemyX, enemyY, imgW, imgH);
        }

        private void drawHUD(Graphics g, int w, int h, int myX, int myY, int enemyX, int enemyY, int imgW, int imgH) {
            
            Color originalColor = g.getColor(); 
            Font oldFont = g.getFont();
            
            // [Req 2 & 4] 폰트 정의
            Font bannerFont = oldFont.deriveFont(Font.BOLD, oldFont.getSize() * 2.0f);
            Font ammoFont = oldFont.deriveFont(Font.BOLD, oldFont.getSize() * 1.5f);

            // 상단 배너
            String turnPlayerName = "P1".equals(currentTurn) ? p1Name : p2Name;
            String banner = (gameOverBanner != null) ? gameOverBanner : (turnPlayerName + "'s Turn");
            
            g.setFont(bannerFont); 
            g.setColor(Color.WHITE);
            int bannerWidth = g.getFontMetrics().stringWidth(banner);
            g.drawString(banner, w/2 - bannerWidth/2, 30); 
            g.setFont(oldFont); 

            // HP 아이콘 크기
            int lifeW = 50, lifeH = 50, gap = 8;

            // HP 및 닉네임 표시
            int myHp = "P1".equals(myRole) ? hp1 : hp2;
            int enemyHp = "P1".equals(myRole) ? hp2 : hp1;
            String myDisplayName = myName; 
            String enemyDisplayName = "P1".equals(myRole) ? p2Name : p1Name;

            // --- 위 플레이어(Enemy) 닉네임 + HP ---
            int enemyHpX = enemyX + imgW + 10;
            int enemyHpY = enemyY + 14; 
            
            g.setFont(ammoFont); 
            g.setColor(new Color(255, 100, 100)); 
            g.drawString(enemyDisplayName, enemyHpX, enemyHpY + 5); 
            
            g.setFont(oldFont);
            g.setColor(Color.WHITE);
            int enemyLifeBottomY = enemyHpY + 20 + lifeH - 16;
            drawLives(g, enemyHpX, enemyLifeBottomY, enemyHp, lifeW, lifeH, gap);
            // 상대방 아이템 가방 표시
            drawItemBag(g, enemyHpX, enemyLifeBottomY + gap + 10, enemyItems, lifeW, lifeH, gap, false);


            // --- 아래 플레이어(Me) 닉네임 + HP ---
            int myHpX = myX + imgW + 10;
            int myHpY = myY + imgH - 10;

            g.setFont(ammoFont); 
            g.setColor(Color.CYAN); 
            g.drawString(myDisplayName, myHpX, myHpY - 30); 

            g.setFont(oldFont);
            g.setColor(Color.WHITE);
            int myLifeBottomY = myHpY;
            drawLives(g, myHpX, myLifeBottomY, myHp, lifeW, lifeH, gap);
            // 내 아이템 가방 표시
            drawItemBag(g, myHpX, myLifeBottomY + gap + 10, myItems, lifeW, lifeH, gap, true);


            // 좌하단: AIM
            String aimText = "AIM: " + currentAim;
            g.drawString(aimText, 10, h - 10);
            
            // 돋보기/폭탄 결과 표시
            if (peekResult != null) {
                g.setFont(ammoFont);
                g.setColor(Color.YELLOW);
                g.drawString(peekResult, 10, h - 30);
                g.setFont(oldFont);
            }
            if (bombStatus != null) {
                g.setFont(ammoFont);
                g.setColor(Color.RED);
                g.drawString(bombStatus, 10, h - 50); 
                g.setFont(oldFont);
            }

            // 우하단: 남은 장탄 수(실탄/공탄)
            String ammoText = "BULLET: " + bulletsLeft;
            String blankText = "BLANK: " + blanksLeft;
            
            g.setFont(ammoFont); 
            
            int textH = g.getFontMetrics().getHeight();
            int ammoW = g.getFontMetrics().stringWidth(ammoText);
            int blankW = g.getFontMetrics().stringWidth(blankText);

            g.setColor(new Color(100, 150, 255)); 
            g.drawString(ammoText, w - ammoW - 10, h - textH - 10);
            
            g.setColor(Color.LIGHT_GRAY); 
            g.drawString(blankText, w - blankW - 10, h - 10);

            // 폰트와 색상을 원래대로 복구
            g.setFont(oldFont);
            g.setColor(originalColor);
        }

        private void drawLives(Graphics g, int x, int y, int hp, int lifeW, int lifeH, int gap) {
            for (int i = 0; i < hp; i++) { 
                int drawX = x + i * (lifeW + gap);
                int drawY = y - lifeH + 16;
                if (lifeImg != null) {
                    g.drawImage(lifeImg, drawX, drawY, lifeW, lifeH, this);
                } else {
                    g.fillRect(drawX, drawY, lifeW, lifeH);
                }
            }
        }
        
        // 아이템 가방 그리기
        private void drawItemBag(Graphics g, int x, int y, String[] items, int itemW, int itemH, int gap, boolean drawIndex) {
            Font oldFont = g.getFont();
            
            int slotW = 40, slotH = 40;
            int slotGap = 5;
            
            for (int i = 0; i < 6; i++) {
                int drawX = x + i * (slotW + slotGap);
                int drawY = y;
                
                // 1. 빈 슬롯 테두리 그리기
                g.setColor(Color.DARK_GRAY);
                g.drawRect(drawX, drawY, slotW, slotH);
                
                // 2. 아이템 이미지 그리기
                String item = (i < items.length) ? items[i] : "-";
                Image itemImg = null;
                
                switch (item) {
                    case "H": itemImg = healImg; break;
                    case "S": itemImg = searchImg; break;
                    case "B": itemImg = bombImg; break;
                    default: break;
                }
                
                if (itemImg != null) {
                    g.drawImage(itemImg, drawX + 3, drawY + 3, slotW - 6, slotH - 6, this);
                }
                
                // 3. 아이템 인덱스 표시 (내 것만)
                if (drawIndex) {
                    g.setFont(oldFont.deriveFont(Font.BOLD, 14f));
                    g.setColor(Color.YELLOW);
                    String indexStr = String.valueOf(i + 1);
                    g.drawString(indexStr, drawX + 2, drawY + 14);
                    g.setFont(oldFont);
                }
            }
        }
    }

    // ====== 채팅 다이얼로그 ======
    class ChatDialog extends JDialog {
        private final JTextArea area = new JTextArea(18, 50);
        private final JTextField input = new JTextField();
        private final int MAX_CHAT_LINES = 500;

        ChatDialog(Window owner) {
            super(owner, "Chat", ModalityType.MODELESS);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setLayout(new BorderLayout());
            area.setEditable(false);
            add(new JScrollPane(area), BorderLayout.CENTER);
            add(input, BorderLayout.SOUTH);
            input.addActionListener(new java.awt.event.ActionListener() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) { doSend(); }
            });
            setSize(560, 420);
            setLocationRelativeTo(owner);
        }

        void appendLine(String s, boolean autoScroll) {
            chatLog.append(s).append('\n');
            area.append(s + "\n");
            int lines = area.getLineCount();
            if (lines > MAX_CHAT_LINES) {
                try {
                    int cut = area.getLineStartOffset(lines - MAX_CHAT_LINES);
                    area.replaceRange("", 0, cut);
                } catch (Exception ignored) {}
            }
            if (autoScroll) area.setCaretPosition(area.getDocument().getLength());
        }

        private void doSend() {
            String txt = input.getText();
            if (txt != null) txt = txt.trim();
            if (txt == null || txt.isEmpty()) return;

            if (!enteredRoom) {
                JOptionPane.showMessageDialog(this, "아직 방에 완전히 입장하지 않았습니다.");
                return;
            }
            net.send(Protocol.CHAT + " " + txt);
            input.setText("");
        }
    }
}