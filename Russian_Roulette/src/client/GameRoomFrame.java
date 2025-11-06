package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

public class GameRoomFrame extends JFrame {
    private final String p1Name;
    private final String p2Name;
    private final String myName;
    private final NetworkClient net;

    private final RoomCanvas canvas;

    // === 내 역할, 전역 채팅창(1개만), 누적 로그 ===
    private final String myRole;
    private ChatDialog chatDialog;
    private final StringBuilder chatLog = new StringBuilder();

    public GameRoomFrame(String p1Name, String p2Name, String myName, NetworkClient net) {
        super("Game Room");
        this.p1Name = p1Name;
        this.p2Name = p2Name;
        this.myName = myName;
        this.net = net;

        this.myRole = myName.equals(p1Name) ? "P1" : (myName.equals(p2Name) ? "P2" : "?");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 680);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        // 중앙: 배경 캔버스
        canvas = new RoomCanvas(p1Name, p2Name);
        // 오버레이용 컨테이너(배경 위에 버튼/라벨 올림)
        JPanel center = new JPanel();
        center.setLayout(new OverlayLayout(center));

        // 오버레이 패널 (투명)
        JPanel overlay = new JPanel(new BorderLayout());
        overlay.setOpaque(false);

        // 화면 상단에 오른쪽 정렬로 버튼/라벨 배치
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        topBar.setOpaque(false);

        // "You: P1/P2 (내 이름)" 라벨
        JLabel youLabel = new JLabel("You: " + myRole + " (" + myName + ")");
        youLabel.setFont(youLabel.getFont().deriveFont(Font.BOLD, 13f));

        // 조작키 버튼
        JButton helpBtn = new JButton("조작키");
        helpBtn.addActionListener(e -> openHelpDialog());

        // Chat 버튼
        JButton chatBtn = new JButton("Chat");
        chatBtn.addActionListener(e -> {
            if (chatDialog != null) {
                chatDialog.setVisible(true);
                chatDialog.toFront();
                chatDialog.focusInput();
            }
        });

        topBar.add(youLabel);
        topBar.add(helpBtn);
        topBar.add(chatBtn);
        overlay.add(topBar, BorderLayout.NORTH);

        center.add(overlay); // 위
        center.add(canvas);  // 아래
        add(center, BorderLayout.CENTER);

        add(Box.createVerticalStrut(6), BorderLayout.SOUTH);

        // 채팅창은 1개만 미리 생성 (열려 있지 않아도 로그 누적 가능)
        chatDialog = new ChatDialog();

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (chatDialog != null) chatDialog.dispose();
            }
        });
    }

    /** 서버 수신 라인 처리자 */
    public Consumer<String> getLineConsumer() {
        return line -> {
            if (line.startsWith("CHAT ")) {
                String payload = line.substring(5).trim();
                String sender = payload;
                String msg = "";
                int idx = payload.indexOf(':');
                if (idx >= 0) {
                    sender = payload.substring(0, idx).trim();
                    msg = payload.substring(idx + 1).trim();
                }

                String role = sender.equals(p1Name) ? "P1" : (sender.equals(p2Name) ? "P2" : "?");
                boolean isMe = sender.equals(myName);
                String display = (role.equals("?") ? "" : "[" + role + "] ")
                        + (isMe ? "[ME] " : "")
                        + sender + ": " + msg;

                // 누적
                chatLog.append(display).append('\n');

                // ★ 수신 시 채팅창 자동 오픈 + 즉시 반영
                if (chatDialog == null) chatDialog = new ChatDialog();
                if (!chatDialog.isVisible()) {
                    chatDialog.setVisible(true);
                    chatDialog.toFront();
                }
                chatDialog.append(display);
            }
        };
    }

    // === 조작 방법 안내 팝업 ===
    private void openHelpDialog() {
        JDialog dlg = new JDialog(this, "조작 방법", true);
        dlg.setSize(360, 260);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout(8,8));

        JTextArea help = new JTextArea(
            "🎮 조작키 안내\n\n" +
            "← / → : 조준 방향 전환\n" +
            "F      : 발사\n" +
            "1~4    : 카드 사용 (내 턴일 때만)\n\n" +
            "기타:\n" +
            "- 턴 시작 시 카드 2장 드로우\n" +
            "- 재장전 시 전원 1장 추가\n" +
            "- SELF+BLANK 재격발, ENEMY는 결과와 무관 교대"
        );
        help.setEditable(false);
        help.setLineWrap(true);
        help.setWrapStyleWord(true);
        help.setFont(help.getFont().deriveFont(13f));
        dlg.add(new JScrollPane(help), BorderLayout.CENTER);

        JButton ok = new JButton("닫기");
        ok.addActionListener(e -> dlg.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(ok);
        dlg.add(south, BorderLayout.SOUTH);

        dlg.setVisible(true);
    }

    // === 캔버스 (배경 + 상/하단 플레이어) ===
    static class RoomCanvas extends JPanel {
        private final String p1Name, p2Name;
        private final BufferedImage bg, p1img, p2img;

        public RoomCanvas(String p1Name, String p2Name) {
            this.p1Name = p1Name;
            this.p2Name = p2Name;
            setPreferredSize(new Dimension(1000, 560));
            bg    = ImageLoader.load("/images/room_bg.png");
            p1img = ImageLoader.load("/images/player1.png");
            p2img = ImageLoader.load("/images/player2.png");
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth(), h = getHeight();

            if (bg != null) g.drawImage(bg, 0, 0, w, h, null);
            else { g.setColor(Color.DARK_GRAY); g.fillRect(0, 0, w, h); }

            int cx = w / 2;
            drawAvatar(g, p1img, p1Name == null ? "P1" : p1Name, cx, (int)(h*0.20));
            drawAvatar(g, p2img, p2Name == null ? "P2" : p2Name, cx, (int)(h*0.80));
        }

        private void drawAvatar(Graphics g, BufferedImage img, String name, int cx, int cy) {
            Graphics2D g2 = (Graphics2D) g.create();
            int boxW = (int)(getWidth() * 0.22);
            int drawW, drawH;
            if (img != null) {
                drawW = boxW;
                drawH = img.getHeight() * drawW / img.getWidth();
                g2.drawImage(img, cx - drawW/2, cy - drawH/2, drawW, drawH, null);
            } else {
                drawW = boxW;
                drawH = (int)(boxW * 0.75);
                g2.setColor(new Color(255,255,255,180));
                g2.fillRoundRect(cx - drawW/2, cy - drawH/2, drawW, drawH, 20, 20);
                g2.setColor(Color.BLACK);
                g2.drawString("IMG", cx - 12, cy + 4);
            }

            g2.setFont(g2.getFont().deriveFont(18f));
            FontMetrics fm = g2.getFontMetrics();
            int nameW = fm.stringWidth(name);
            g2.setColor(Color.WHITE);
            g2.drawString(name, cx - nameW/2, cy + drawH/2 + fm.getAscent() + 4);
            g2.dispose();
        }
    }

    // === 채팅창 (항상 1개) ===
    class ChatDialog extends JDialog {
        private final JTextArea area = new JTextArea();
        private final JTextField input = new JTextField();
        private final JButton sendBtn = new JButton("Send");

        ChatDialog() {
            super(GameRoomFrame.this, "Chat - You: " + myRole + " (" + myName + ")", false);
            setSize(520, 400);
            setLocationRelativeTo(GameRoomFrame.this);
            setLayout(new BorderLayout(6,6));
            setDefaultCloseOperation(HIDE_ON_CLOSE);

            area.setEditable(false);
            add(new JScrollPane(area), BorderLayout.CENTER);

            JPanel bottom = new JPanel(new BorderLayout(6,6));
            input.setToolTipText("메시지 입력 - You: " + myRole + " (" + myName + ")");
            bottom.add(input, BorderLayout.CENTER);
            bottom.add(sendBtn, BorderLayout.EAST);
            add(bottom, BorderLayout.SOUTH);

            // Enter/Send → 서버 전송 (로컬 에코 없음)
            input.addActionListener(e -> doSend());
            sendBtn.addActionListener(e -> doSend());

            // 창 활성화될 때마다 누적 로그 싱크
            addWindowListener(new WindowAdapter() {
                @Override public void windowActivated(WindowEvent e) {
                    area.setText(chatLog.toString());
                    area.setCaretPosition(area.getDocument().getLength());
                }
            });
        }

        private void doSend() {
            String txt = input.getText();
            if (txt != null && !txt.trim().isEmpty()) {
                net.send("CHAT " + txt.trim());
                input.setText("");
            }
        }

        void append(String line) {
            // ★ 닫혀 있어도 항상 area에 누적되도록 변경 (중요)
            area.append(line + "\n");
            area.setCaretPosition(area.getDocument().getLength());
        }

        void focusInput() {
            SwingUtilities.invokeLater(() -> input.requestFocusInWindow());
        }
    }
}
