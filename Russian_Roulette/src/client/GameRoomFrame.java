package client;

import server.Protocol;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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

    // === 플레이어별 조준 상태 ===
    private String p1Aim = "ENEMY";
    private String p2Aim = "ENEMY";
    
    // === [Item] 아이템 가방 절대 상태 (P1, P2 기준) ===
    private String[] p1Items = new String[6]; 
    private String[] p2Items = new String[6]; 
    private String peekResult = null; 
    private String bombStatus = null; 
    // === [Item] 끝 ===

    // ===== 총 회전(애니메이션) =====
    private double currentAngleRad = 0.0;     
    private double targetAngleRad  = 0.0;     
    private Timer rotTimer;             
    private final double ROT_STEP = Math.toRadians(12); 

    public GameRoomFrame(String p1Name, String p2Name, String myName, NetworkClient net, 
                         int initialBullets, int initialBlanks,
                         String p1InitialItems, String p2InitialItems) { 
        super("Game Room");
        this.p1Name = p1Name; this.p2Name = p2Name; this.myName = myName; this.net = net;
        this.myRole = myName.equals(p1Name) ? "P1" : "P2";

        this.bulletsLeft = initialBullets;
        this.blanksLeft = initialBlanks;
        
        setItemsFromStrings(p1InitialItems, this.p1Items);
        setItemsFromStrings(p2InitialItems, this.p2Items);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (JOptionPane.showConfirmDialog(GameRoomFrame.this, "정말 나가시겠습니까?", "종료 확인", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    net.send(Protocol.EXIT_ROOM); 
                    net.close(); 
                    dispose();
                }
            }
        });

        setSize(960, 640);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        canvas = new RoomCanvas();
        add(canvas, BorderLayout.CENTER);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        JLabel youLabel = new JLabel("You: " + myRole + " (" + myName + ")");
        topBar.add(youLabel, BorderLayout.WEST);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton keyBtn  = new JButton("조작키");
        keyBtn.setFocusable(false);
        JButton chatBtn = new JButton("Chat");
        chatBtn.setFocusable(false);
        
        keyBtn.addActionListener(e -> showKeyHelp());
        chatBtn.addActionListener(e -> ensureChatDialog());
        rightButtons.add(keyBtn);
        rightButtons.add(chatBtn);
        rightButtons.setOpaque(false);
        topBar.add(rightButtons, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        rotTimer = new Timer(16, e -> {
            double diff = targetAngleRad - currentAngleRad;
            if (Math.abs(diff) <= ROT_STEP) {
                currentAngleRad = targetAngleRad;
                rotTimer.stop();
            } else {
                currentAngleRad += Math.signum(diff) * ROT_STEP;
            }
            canvas.repaint();
        });
        rotTimer.setRepeats(true);

        setupKeyBindings(); 
        net.setOnLine(getLineConsumer());
        
        SwingUtilities.invokeLater(() -> this.requestFocusInWindow());
    }
    
    private void setItemsFromStrings(String itemsStr, String[] targetArray) {
        if (itemsStr == null || itemsStr.isEmpty()) {
            Arrays.fill(targetArray, "-");
            return;
        }
        String[] items = itemsStr.split(","); 
        for (int i = 0; i < targetArray.length; i++) {
            targetArray[i] = (i < items.length) ? items[i] : "-";
        }
    }

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
        return line -> SwingUtilities.invokeLater(() -> handleServerLine(line));
    }

    private void handleServerLine(String line) {
        if (line == null) return;
        
        if (line.startsWith(Protocol.ENTER_ROOM)) {
            enteredRoom = true;
            return;
        }

        if (line.startsWith(Protocol.CHAT + " ")) {
            String msg = line.substring(Protocol.CHAT.length() + 1).trim();
            ensureChatDialog(); 
            chatDialog.appendLine(msg, true); 
            return;
        }

        if (line.startsWith(Protocol.TURN + " ")) {
            String who = line.substring((Protocol.TURN + " ").length()).trim();
            if (who.equals("P1") || who.equals("P2")) currentTurn = who;
            
            if (myRole.equals(currentTurn)) {
                bombStatus = null;
            }
            updateGunAngleForCurrentTurn(); 
            canvas.repaint();
            return;
        }
        
        if (line.startsWith(Protocol.AIM_UPDATE + " ")) {
            String who = parseKV(line, "WHO");
            String target = parseKV(line, "TARGET");
            
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
            peekResult = null; 
            bombStatus = null; 
            parseFireResolve(line);
            canvas.repaint();
            return;
        }

        if (line.startsWith(Protocol.GAME_OVER + " ")) {
            String win = parseKV(line, "WIN");
            gameOverBanner = "GAME OVER - WIN: " + win;
            canvas.repaint();
            return;
        }
        
        if (line.startsWith(Protocol.ITEM_UPDATE + " ")) {
            parseItemUpdate(line);
            canvas.repaint();
            return;
        }
        
        if (line.startsWith(Protocol.PEEK_RESULT + " ")) {
            String result = parseKV(line, "TYPE");
            peekResult = "총알 확인: " + ("BULLET".equals(result) ? "실탄" : "공탄"); 
            canvas.repaint(); 
            return;
        }

        if (line.startsWith(Protocol.EXIT_ROOM)) {
             JOptionPane.showMessageDialog(this, "상대방이 나갔습니다. 게임 종료.");
             net.close();
             dispose();
        }
    }

    private void parseReload(String line) {
        try {
            int slash = line.indexOf('/');
            int spaceAfter = line.indexOf(' ', slash);
            if (slash > 0 && spaceAfter > slash) {
                String left = line.substring(Protocol.RELOAD.length() + 1, slash).trim();
                shotIndex = Integer.parseInt(left); 
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
    
    private void parseItemUpdate(String line) {
        String who = parseKV(line, "WHO");
        String itemsStr = parseKV(line, "ITEMS");
        if (itemsStr == null) return;
        
        if ("P1".equals(who)) {
            setItemsFromStrings(itemsStr, p1Items); 
        } else if ("P2".equals(who)) {
            setItemsFromStrings(itemsStr, p2Items); 
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
        
        String aimEnemyAction = "AIM_ENEMY";
        String aimSelfAction  = "AIM_SELF";
        
        if ("P2".equals(myRole)) {
            c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("UP"),   aimSelfAction);
            c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("DOWN"), aimEnemyAction);
        } else {
            c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("UP"),   aimEnemyAction);
            c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("DOWN"), aimSelfAction);
        }
        
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true), "FIRE_ACTION");
        
        for (int i = 1; i <= 6; i++) {
            c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_0 + i, 0, true), 
                "USE_ITEM_" + i
            );
            int slot = i;
            c.getActionMap().put("USE_ITEM_" + i, new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                    tryUseItem(slot); 
                }
            });
        }
        
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
        
        boolean isMyTurn = myRole.equals(currentTurn);
        String[] myItemArray = "P1".equals(myRole) ? p1Items : p2Items;
        int myHp = "P1".equals(myRole) ? hp1 : hp2;
        String item = myItemArray[slot - 1]; 
        
        if (slot < 1 || slot > myItemArray.length || "-".equals(item)) {
             return;
        }

        if ("H".equals(item) && !isMyTurn) return;
        
        if (gameOverBanner != null) return; 
        
        if ("H".equals(item)) {
            if (myHp >= MAX_HP) {
                 JOptionPane.showMessageDialog(this, "HP가 가득 차서 Heal을 사용할 수 없습니다.");
                 return;
            }
        }
        
        myItemArray[slot - 1] = "-"; 
             
        if ("B".equals(item)) {
           bombStatus = "강화!!! (다음 발사 실탄 데미지 2배)";
        }
        
        canvas.repaint(); 
        net.send(Protocol.USE_ITEM + " SLOT=" + slot);
    }

    private void updateGunAngleForCurrentTurn() {
        String targetAim;
        if ("P1".equals(currentTurn)) {
            targetAim = p1Aim;
        } else {
            targetAim = p2Aim;
        }
        
        if ("P1".equals(currentTurn)) {
            targetAngleRad = "ENEMY".equals(targetAim) ? -Math.PI/2 : Math.PI/2;
        } else {
            targetAngleRad = "ENEMY".equals(targetAim) ? Math.PI/2 : -Math.PI/2;
        }
        
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
            ImageIcon bgIcon   = ImageLoader.load("images/room_bg.png");
            ImageIcon p1Icon   = ImageLoader.load("images/player1.png");
            ImageIcon p2Icon   = ImageLoader.load("images/player2.png");
            ImageIcon gunIcon  = ImageLoader.load("images/gun.png");
            ImageIcon lifeIcon = ImageLoader.load("images/life.png");
            
            ImageIcon healIcon   = ImageLoader.load("images/Heal.png"); 
            ImageIcon searchIcon = ImageLoader.load("images/Search.png");
            ImageIcon bombIcon   = ImageLoader.load("images/bomb.png");

            bg     = (bgIcon == null)  ? null : bgIcon.getImage();
            p1Img  = (p1Icon == null)  ? null : p1Icon.getImage();
            p2Img  = (p2Icon == null)  ? null : p2Icon.getImage();
            gunImg = (gunIcon == null) ? null : gunIcon.getImage();
            lifeImg= (lifeIcon == null)? null : lifeIcon.getImage();
            
            healImg   = (healIcon == null) ? null : healIcon.getImage();
            searchImg = (searchIcon == null) ? null : searchIcon.getImage();
            bombImg   = (bombIcon == null) ? null : bombIcon.getImage();
        }


        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth(), h = getHeight();

            if (bg != null) g.drawImage(bg, 0, 0, w, h, this);
            else {
                g.setColor(new Color(40, 40, 40));
                g.fillRect(0, 0, w, h);
            }

            // 캐릭터 크기: 화면 너비의 20%
            double avatarScale = 0.20; 
            int imgW = (int)(w * avatarScale);
            int imgH = imgW; // 정사각형

            // 상하단 여백 0.5% (최소화하여 위아래 끝으로 밀착)
            int marginY = (int)(h * 0.005);
            
            int centerX = (w - imgW) / 2;

            int p1X = centerX;
            int p1Y = h - imgH - marginY; 
            
            int p2X = centerX;
            int p2Y = marginY;

            if (p2Img != null) g.drawImage(p2Img, p2X, p2Y, imgW, imgH, this);
            if (p1Img != null) g.drawImage(p1Img, p1X, p1Y, imgW, imgH, this);
            
            // [수정] 총 크기를 화면 너비의 22%로 줄여서 P2와의 간격 확보 (0.3 -> 0.22)
            double gunRatio = 0.22; 
            int gunW = (int)(w * gunRatio);
            int gunH = (gunImg != null) ? (gunImg.getHeight(this) * gunW / gunImg.getWidth(this)) : gunW;
            
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

            drawHUD(g, w, h, p1X, p1Y, p2X, p2Y, imgW, imgH);
        }

        private void drawHUD(Graphics g, int w, int h, int p1X, int p1Y, int p2X, int p2Y, int imgW, int imgH) {
            
            Color originalColor = g.getColor(); 
            Font oldFont = g.getFont();
            
            // 폰트 크기 (1.4%)
            int baseFontSize = Math.max(12, (int)(w * 0.014));
            Font bannerFont = oldFont.deriveFont(Font.BOLD, baseFontSize * 2.0f);
            Font ammoFont   = oldFont.deriveFont(Font.BOLD, baseFontSize * 1.3f);

            String turnPlayerName = "P1".equals(currentTurn) ? p1Name : p2Name;
            String banner = (gameOverBanner != null) ? gameOverBanner : (turnPlayerName + "'s Turn");
            
            // 턴 알림 텍스트: 왼쪽 상단 고정
            g.setFont(bannerFont); 
            g.setColor(Color.WHITE);
            // 위치: x=20, y=높이의 6% 지점
            g.drawString(banner, 20, (int)(h * 0.06)); 
            g.setFont(oldFont); 

            // 아이콘/하트 크기 (캐릭터의 18%)
            int lifeW = (int)(imgW * 0.18); 
            int lifeH = lifeW; 
            int gap = (int)(w * 0.008); 

            int p1Hp = hp1;
            int p2Hp = hp2;
            String p1DisplayName = p1Name; 
            String p2DisplayName = p2Name;

            int textGapX = (int)(w * 0.01); 

            // --- P2 플레이어(위) HUD ---
            int p2UiX = p2X + imgW + textGapX;
            int p2NameY = p2Y + (int)(imgH * 0.2); 
            
            g.setFont(ammoFont); 
            g.setColor(new Color(255, 100, 100)); 
            g.drawString(p2DisplayName, p2UiX, p2NameY); 
            
            g.setFont(oldFont);
            g.setColor(Color.WHITE);
            
            int p2LifeY = p2NameY + (int)(h * 0.015); 
            drawLives(g, p2UiX, p2LifeY, p2Hp, lifeW, lifeH, gap);
            
            int p2ItemY = p2LifeY + lifeH + gap;
            drawItemBag(g, p2UiX, p2ItemY, p2Items, lifeW, lifeH, gap, "P2".equals(myRole));


            // --- P1 플레이어(아래) HUD ---
            int p1UiX = p1X + imgW + textGapX;
            
            // P1 이름 위치 (캐릭터 상단에서 40% 내려온 지점)
            int p1NameY = p1Y + (int)(imgH * 0.4); 
            
            g.setFont(ammoFont); 
            g.setColor(Color.CYAN); 
            g.drawString(p1DisplayName, p1UiX, p1NameY); 
            
            g.setFont(oldFont);
            g.setColor(Color.WHITE);
            
            int p1LifeY = p1NameY + (int)(h * 0.015); 
            drawLives(g, p1UiX, p1LifeY, p1Hp, lifeW, lifeH, gap);
            
            int p1ItemY = p1LifeY + lifeH + gap;
            drawItemBag(g, p1UiX, p1ItemY, p1Items, lifeW, lifeH, gap, "P1".equals(myRole));

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

            g.setFont(oldFont);
            g.setColor(originalColor);
        }

        private void drawLives(Graphics g, int x, int y, int hp, int lifeW, int lifeH, int gap) {
            for (int i = 0; i < hp; i++) { 
                int drawX = x + i * (lifeW + gap);
                int drawY = y;
                if (lifeImg != null) {
                    g.drawImage(lifeImg, drawX, drawY, lifeW, lifeH, this);
                } else {
                    g.fillRect(drawX, drawY, lifeW, lifeH);
                }
            }
        }
        
        private void drawItemBag(Graphics g, int x, int y, String[] items, int itemW, int itemH, int gap, boolean drawIndex) {
            Font oldFont = g.getFont();
            
            int slotW = itemW; 
            int slotH = itemH;
            int slotGap = gap;
            
            for (int i = 0; i < 6; i++) {
                int drawX = x + i * (slotW + slotGap);
                int drawY = y;
                
                g.setColor(Color.DARK_GRAY);
                g.drawRect(drawX, drawY, slotW, slotH);
                
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
                
                if (drawIndex) {
                    g.setFont(oldFont.deriveFont(Font.BOLD, (float)(itemH * 0.4))); 
                    g.setColor(Color.YELLOW);
                    String indexStr = String.valueOf(i + 1);
                    g.drawString(indexStr, drawX + 2, drawY + (int)(slotH * 0.35));
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
            input.addActionListener(e -> doSend());
            setSize(560, 420);
            setLocationRelativeTo(owner);
            area.setText(chatLog.toString());
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