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
    private String currentAim = "ENEMY"; // SELF | ENEMY

    // ===== 서버 방송 상태(표시용) =====
    private int hp1 = 5;
    private int hp2 = 5;
    private static final int MAX_HP = 5;
    private String currentTurn = "P1"; // "P1" | "P2"
    private int shotIndex = 0;         // 0~6
    private int bulletsLeft = 0;       // 남은 실탄 수
    private int blanksLeft  = 0;       // 남은 공탄 수
    private String gameOverBanner = null;

    // ===== 총 회전(애니메이션) =====
    // 기본 총 이미지는 "오른쪽"을 봄. 목표 각도는 절대각: 오른쪽=0, 위=-90°, 아래=+90°.
    private double currentAngleRad = 0.0;      // 현재 각도
    private double targetAngleRad  = 0.0;      // 목표 각도
    private final Timer rotTimer;              // 부드러운 회전용 타이머
    private final double ROT_STEP = Math.toRadians(12); // 틱당 12도

    public GameRoomFrame(String p1Name, String p2Name, String myName, NetworkClient net) {
        super("Game Room");
        this.p1Name = p1Name; this.p2Name = p2Name; this.myName = myName; this.net = net;
        this.myRole = myName.equals(p1Name) ? "P1" : "P2";

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
                "←/→: 조준 이동 (SELF/ENEMY)\nF 또는 f: 발사",
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
            canvas.repaint();
            return;
        }

        if (line.startsWith(Protocol.RELOAD + " ")) {
            parseReload(line);   // RELOAD 0/6 B=.. K=..
            canvas.repaint();
            return;
        }

        if (line.startsWith(Protocol.FIRE_RESOLVE + " ")) {
            parseFireResolve(line); // HP/탄/샷 인덱스 갱신
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
            // 자동 오픈/알럿 없음 (원하면 Chat 버튼으로 열어 확인)
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
        // PRESS
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("LEFT"),  "AIM_SELF");
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("RIGHT"), "AIM_ENEMY");
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('F'),     "FIRE_PRESS");
        // TYPED (소문자/대문자)
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('f'), "FIRE_TYPED_f");
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('F'), "FIRE_TYPED_F");

        c.getActionMap().put("AIM_SELF", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                currentAim = "SELF";
                setTargetAngleForAim();   // 아래(+90°)로 회전 목표
                net.send(Protocol.AIM + " SELF");
            }
        });
        c.getActionMap().put("AIM_ENEMY", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                currentAim = "ENEMY";
                setTargetAngleForAim();   // 위(-90°)로 회전 목표
                net.send(Protocol.AIM + " ENEMY");
            }
        });
        c.getActionMap().put("FIRE_PRESS", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { tryFire(); }
        });
        c.getActionMap().put("FIRE_TYPED_f", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { tryFire(); }
        });
        c.getActionMap().put("FIRE_TYPED_F", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { tryFire(); }
        });
    }

    private void setTargetAngleForAim() {
        // 오른쪽=0, 위=-90°, 아래=+90°
        targetAngleRad = "ENEMY".equals(currentAim) ? -Math.PI/2 : Math.PI/2;
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
                        if (code == KeyEvent.VK_LEFT) {
                            currentAim = "SELF";
                            setTargetAngleForAim();
                            net.send(Protocol.AIM + " SELF");
                            return true;
                        } else if (code == KeyEvent.VK_RIGHT) {
                            currentAim = "ENEMY";
                            setTargetAngleForAim();
                            net.send(Protocol.AIM + " ENEMY");
                            return true;
                        } else if (code == KeyEvent.VK_F) {
                            tryFire();
                            return true;
                        }
                    }
                    if (e.getID() == KeyEvent.KEY_TYPED) {
                        char ch = e.getKeyChar();
                        if (ch == 'f' || ch == 'F') {
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
        private final Image lifeImg;  // ★ HP 아이콘

        RoomCanvas() {
            ImageIcon bgIcon   = ImageLoader.load("images/room_bg.png");
            ImageIcon p1Icon   = ImageLoader.load("images/player1.png");
            ImageIcon p2Icon   = ImageLoader.load("images/player2.png");
            ImageIcon gunIcon  = ImageLoader.load("images/gun.png");   // 가운데 총
            ImageIcon lifeIcon = ImageLoader.load("images/life.png");  // HP 아이콘(투명 PNG 권장)

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

            // P2: 위 중앙
            int p2X = centerX;
            int p2Y = marginTop;
            if (p2Img != null) g.drawImage(p2Img, p2X, p2Y, imgW, imgH, this);

            // P1: 아래 중앙
            int p1X = centerX;
            int p1Y = h - imgH - marginBottom;
            if (p1Img != null) g.drawImage(p1Img, p1X, p1Y, imgW, imgH, this);

            // ===== 총(회전) =====
            final int gunW = 140, gunH = 140;
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
            drawHUD(g, w, h, p1X, p1Y, p2X, p2Y, imgW, imgH);
        }

        private void drawHUD(Graphics g, int w, int h, int p1X, int p1Y, int p2X, int p2Y, int imgW, int imgH) {
            g.setColor(Color.WHITE);

            // 상단 배너
            String banner = (gameOverBanner != null) ? gameOverBanner : ("TURN: " + currentTurn);
            g.drawString(banner, w/2 - g.getFontMetrics().stringWidth(banner)/2, 20);

            // HP 아이콘(좌→우로 최대 5개)
            int lifeW = 20, lifeH = 20, gap = 6;

            // 위 플레이어(P2) HP: 이미지 오른쪽 옆에 가로로
            int p2HpX = p2X + imgW + 10;
            int p2HpY = p2Y + 14;
            drawLives(g, p2HpX, p2HpY, hp2, lifeW, lifeH, gap);

            // 아래 플레이어(P1) HP
            int p1HpX = p1X + imgW + 10;
            int p1HpY = p1Y + imgH - 10;
            drawLives(g, p1HpX, p1HpY, hp1, lifeW, lifeH, gap);

            // 좌하단: AIM/SHOT
            String aimText = "AIM: " + currentAim + "   SHOT: " + shotIndex + "/6";
            g.drawString(aimText, 10, h - 10);

            // 우하단: 남은 장탄 수(실탄/공탄)
            String ammoText = "Ammo Left  BULLET: " + bulletsLeft + "   BLANK: " + blanksLeft;
            int textW = g.getFontMetrics().stringWidth(ammoText);
            g.drawString(ammoText, w - textW - 10, h - 10);
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
                } else {
                    // 빈 하트 표현(아이콘 없을 경우 대비)
                    g.drawRect(drawX, y - lifeH + 16, lifeW, lifeH);
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
            net.send(Protocol.CHAT + " " + txt); // 로컬 echo 금지
            input.setText("");
        }
    }
}
