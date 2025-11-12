package client;

import server.Protocol;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
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


    // ===== 총 회전(애니메이션) =====
    private double currentAngleRad = 0.0;      // 현재 각도
    private double targetAngleRad  = 0.0;      // 목표 각도
    private final Timer rotTimer;              // 부드러운 회전용 타이머
    private final double ROT_STEP = Math.toRadians(12); // 틱당 12도

    // === [Req 3-3] 생성자 수정: initialBullets, initialBlanks 추가 ===
    public GameRoomFrame(String p1Name, String p2Name, String myName, NetworkClient net, 
                         int initialBullets, int initialBlanks) {
        super("Game Room");
        this.p1Name = p1Name; this.p2Name = p2Name; this.myName = myName; this.net = net;
        this.myRole = myName.equals(p1Name) ? "P1" : "P2";

        // === [Req 3-3] 생성 시점에서 초기 총알 상태 설정 ===
        this.bulletsLeft = initialBullets;
        this.blanksLeft = initialBlanks;
        // === [Req 3-3] 끝 ===

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
        JButton chatBtn = new JButton("Chat");
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
        installGlobalKeyDispatcher();
        net.setOnLine(getLineConsumer());
    }

    private void showKeyHelp() {
        JOptionPane.showMessageDialog(this,
                "↑: 조준 이동 (ENEMY)\n" +
                "↓: 조준 이동 (SELF)\n" +
                "SPACE: 발사",
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
            return;
        }


        if (line.startsWith(Protocol.RELOAD + " ")) {
            parseReload(line);
            canvas.repaint();
            return;
        }

        if (line.startsWith(Protocol.FIRE_RESOLVE + " ")) {
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

        if (line.startsWith(Protocol.CHAT + " ")) {
            if (chatDialog != null && chatDialog.isVisible()) {
                String payload = line.substring(Protocol.CHAT.length() + 1).trim();
                int idx = payload.indexOf(':');
                String sender = (idx >= 0) ? payload.substring(0, idx).trim() : payload;
                String msg    = (idx >= 0) ? payload.substring(idx + 1).trim() : "";
                String role   = sender.equals(p1Name) ? "P1" : (sender.equals(p2Name) ? "P2" : "?");
                boolean isMe  = sender.equals(myName);
                String display = (role.equals("?") ? "" : "[" + role + "] ")
                               + (isMe ? "[ME] " : "")
                               + sender + ": " + msg;
                chatDialog.appendLine(display, true);
            }
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
        }
    }

    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return def; }
    }

    private void setupKeyBindings() {
        JComponent c = getRootPane();
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("DOWN"),  "AIM_SELF");
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("UP"), "AIM_ENEMY");
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "FIRE_ACTION");
        
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

    private void updateGunAngleForCurrentTurn() {
        String targetAim;
        if ("P1".equals(currentTurn)) {
            targetAim = p1Aim;
        } else {
            targetAim = p2Aim;
        }
        
        targetAngleRad = "ENEMY".equals(targetAim) ? -Math.PI/2 : Math.PI/2;
        if (!rotTimer.isRunning()) rotTimer.start();
        canvas.repaint();
    }

    private void tryFire() {
        if (!myRole.equals(currentTurn)) { return; }
        net.send(Protocol.FIRE);
    }

    private void installGlobalKeyDispatcher() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .addKeyEventDispatcher(new KeyEventDispatcher() {
                @Override
                public boolean dispatchKeyEvent(KeyEvent e) {
                    if (e.getID() == KeyEvent.KEY_PRESSED) {
                        int code = e.getKeyCode();
                        
                        if (code == KeyEvent.VK_DOWN) {
                            if (!myRole.equals(currentTurn)) return true;
                            currentAim = "SELF";
                            net.send(Protocol.AIM + " SELF");
                            return true;
                        } else if (code == KeyEvent.VK_UP) {
                            if (!myRole.equals(currentTurn)) return true;
                            currentAim = "ENEMY";
                            net.send(Protocol.AIM + " ENEMY");
                            return true;
                        } else if (code == KeyEvent.VK_SPACE) {
                            tryFire();
                            return true;
                        }
                    }
                    return false;
                }
            });
    }

    // ====== 캔버스(배경/플레이어/총/표시) ======
    class RoomCanvas extends JPanel {
        private final Image bg;
        private final Image p1Img;
        private final Image p2Img;
        private final Image gunImg;
        private final Image lifeImg;

        RoomCanvas() {
            ImageIcon bgIcon   = ImageLoader.load("images/room_bg.png");
            ImageIcon p1Icon   = ImageLoader.load("images/player1.png");
            ImageIcon p2Icon   = ImageLoader.load("images/player2.png");
            ImageIcon gunIcon  = ImageLoader.load("images/gun.png");
            ImageIcon lifeIcon = ImageLoader.load("images/life.png");

            bg     = (bgIcon == null)  ? null : bgIcon.getImage();
            p1Img  = (p1Icon == null)  ? null : p1Icon.getImage();
            p2Img  = (p2Icon == null)  ? null : p2Icon.getImage();
            gunImg = (gunIcon == null) ? null : gunIcon.getImage();
            lifeImg= (lifeIcon == null)? null : lifeIcon.getImage();
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

            // HUD(턴/HP/탄/샷)
            drawHUD(g, w, h, myX, myY, enemyX, enemyY, imgW, imgH);
        }

        private void drawHUD(Graphics g, int w, int h, int myX, int myY, int enemyX, int enemyY, int imgW, int imgH) {
            
            // === [Req 2 & 4] 폰트 정의 ===
            Font oldFont = g.getFont();
            // [Req 2] 턴 배너용 폰트 (2.0배 굵게)
            Font bannerFont = oldFont.deriveFont(Font.BOLD, oldFont.getSize() * 2.0f);
            // [Req 4] 닉네임 및 총알용 폰트 (1.5배 굵게)
            Font ammoFont = oldFont.deriveFont(Font.BOLD, oldFont.getSize() * 1.5f);
            // === 끝 ===

            // === [Req 2] 상단 배너 (닉네임 사용 및 폰트 키움) ===
            String turnPlayerName = "P1".equals(currentTurn) ? p1Name : p2Name;
            String banner = (gameOverBanner != null) ? gameOverBanner : (turnPlayerName + "'s Turn");
            
            g.setFont(bannerFont); // 큰 폰트 적용
            g.setColor(Color.WHITE);
            int bannerWidth = g.getFontMetrics().stringWidth(banner);
            g.drawString(banner, w/2 - bannerWidth/2, 30); // Y좌표 20 -> 30
            g.setFont(oldFont); // 폰트 리셋
            // === [Req 2] 끝 ===


            // === [Req 1] HP 아이콘 크기 키움 (20x20 -> 30x30) ===
            int lifeW = 50, lifeH = 50, gap = 8;
            // === [Req 1] 끝 ===


            // === HP 및 닉네임 표시 ===
            int myHp = "P1".equals(myRole) ? hp1 : hp2;
            int enemyHp = "P1".equals(myRole) ? hp2 : hp1;
            // [Req 1] "ME", "ENEMY" 대신 닉네임 사용
            String myDisplayName = myName; 
            String enemyDisplayName = "P1".equals(myRole) ? p2Name : p1Name;

            // --- 위 플레이어(Enemy) 닉네임 + HP ---
            int enemyHpX = enemyX + imgW + 10;
            int enemyHpY = enemyY + 14; 
            
            g.setFont(ammoFont); // 1.5배 굵은 폰트
            g.setColor(new Color(255, 100, 100)); // 적색
            g.drawString(enemyDisplayName, enemyHpX, enemyHpY + 5); // [Req 1]
            
            g.setFont(oldFont);
            g.setColor(Color.WHITE);
            drawLives(g, enemyHpX, enemyHpY + 20, enemyHp, lifeW, lifeH, gap);

            // --- 아래 플레이어(Me) 닉네임 + HP ---
            int myHpX = myX + imgW + 10;
            int myHpY = myY + imgH - 10;

            g.setFont(ammoFont); // 1.5배 굵은 폰트
            g.setColor(Color.CYAN); // 내 식별색
            g.drawString(myDisplayName, myHpX, myHpY - 30); // [Req 1]

            g.setFont(oldFont);
            g.setColor(Color.WHITE);
            drawLives(g, myHpX, myHpY, myHp, lifeW, lifeH, gap);
            // === 끝 ===


            // 좌하단: AIM
            String aimText = "AIM: " + currentAim;
            g.drawString(aimText, 10, h - 10);

            // === [Req 4] 우하단: 남은 장탄 수(실탄/공탄) - 크기 키움 ===
            String ammoText = "BULLET: " + bulletsLeft;
            String blankText = "BLANK: " + blanksLeft;
            
            g.setFont(ammoFont); // 1.5배 굵은 폰트 적용
            
            int textH = g.getFontMetrics().getHeight();
            int ammoW = g.getFontMetrics().stringWidth(ammoText);
            int blankW = g.getFontMetrics().stringWidth(blankText);

            g.setColor(new Color(100, 150, 255)); // 실탄 (파란색)
            g.drawString(ammoText, w - ammoW - 10, h - textH - 10);
            
            g.setColor(Color.LIGHT_GRAY); // 공탄 (회색)
            g.drawString(blankText, w - blankW - 10, h - 10);

            g.setFont(oldFont); // 폰트 원상 복구
            // === [Req 4] 끝 ===
        }

        private void drawLives(Graphics g, int x, int y, int hp, int lifeW, int lifeH, int gap) {
            // hp 개수만큼 life.png를 그리고, (MAX_HP - hp)만큼은 테두리 사각형(혹은 흐릿하게)로 빈칸 표현
            for (int i = 0; i < MAX_HP; i++) {
                int drawX = x + i * (lifeW + gap);
                if (i < hp) {
                    if (lifeImg != null) {
                        g.drawImage(lifeImg, drawX, y - lifeH + 16, lifeW, lifeH, this);
                    } else {
                        g.fillRect(drawX, y - lifeH + 16, lifeW, lifeH);
                    }
                } 
                // === [Req 2] else 블록 제거: 빈 하트(사각형)를 그리지 않음 ===
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