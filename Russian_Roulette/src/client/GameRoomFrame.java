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

    // 채팅 관련
    private ChatDialog chatDialog;                // 팝업 창(필요할 때 생성)
    private final StringBuilder chatLog = new StringBuilder(); // 창이 닫혀 있어도 누적

    public GameRoomFrame(String p1Name, String p2Name, String myName, NetworkClient net) {
        super("Game Room");
        this.p1Name = p1Name;
        this.p2Name = p2Name;
        this.myName = myName;
        this.net = net;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 680);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        // 중앙: 배경 + 플레이어(상단/하단)
        canvas = new RoomCanvas(p1Name, p2Name);
        add(canvas, BorderLayout.CENTER);

        // 하단: Chat 버튼만
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton chatBtn = new JButton("Chat");
        chatBtn.addActionListener(e -> openChat());
        bottomBar.add(chatBtn);
        add(bottomBar, BorderLayout.SOUTH);

        // 메인 창 닫힐 때 채팅창도 정리
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (chatDialog != null) chatDialog.dispose();
            }
        });
    }

    /** 서버 수신 라인 처리자 (RoomFrame에서 setOnLine으로 연결됨) */
    public Consumer<String> getLineConsumer() {
        return line -> {
            if (line.startsWith("CHAT ")) {
                String msg = line.substring(5).trim(); // "Sender: message"
                // 수신 시 자동으로 채팅창 열기 + 누적/표시
                appendChat(msg, /*autoOpen=*/true);
            }
        };
    }

    /** 채팅 로그 누적 + 창이 열려 있으면 즉시 표시, autoOpen이면 창 자동 오픈 */
    private void appendChat(String msg, boolean autoOpen) {
        chatLog.append(msg).append('\n');
        if (autoOpen && (chatDialog == null || !chatDialog.isVisible())) {
            openChat(); // 최초 수신 시 자동으로 창 열기
        }
        if (chatDialog != null) chatDialog.append(msg);
    }

    /** Chat 버튼 동작: 창을 생성/보여주고, 기존 누적 로그를 동기화 */
    private void openChat() {
        if (chatDialog == null) {
            chatDialog = new ChatDialog();
        }
        chatDialog.setVisible(true);
        chatDialog.setAllText(chatLog.toString());
        chatDialog.toFront();
        chatDialog.focusInput();
    }

    // ===== 캔버스: 배경 + 플레이어(상단/하단 중앙 정렬) =====
    static class RoomCanvas extends JPanel {
        private final String p1Name;
        private final String p2Name;
        private final BufferedImage bg;
        private final BufferedImage p1img;
        private final BufferedImage p2img;

        public RoomCanvas(String p1Name, String p2Name) {
            this.p1Name = p1Name;
            this.p2Name = p2Name;
            setPreferredSize(new Dimension(1000, 560));
            // resources/images 를 Source로 등록했다면 /images/... 경로가 맞음
            bg    = ImageLoader.load("/images/room_bg.png");
            p1img = ImageLoader.load("/images/player1.png");
            p2img = ImageLoader.load("/images/player2.png");
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth(), h = getHeight();

            // 배경
            if (bg != null) g.drawImage(bg, 0, 0, w, h, null);
            else {
                g.setColor(Color.DARK_GRAY);
                g.fillRect(0, 0, w, h);
            }

            // 플레이어1: 상단 중앙
            int cx = w / 2;
            int topY = (int)(h * 0.20);
            drawAvatarWithName(g, p1img, p1Name == null ? "P1" : p1Name, cx, topY, true);

            // 플레이어2: 하단 중앙
            int bottomY = (int)(h * 0.80);
            drawAvatarWithName(g, p2img, p2Name == null ? "P2" : p2Name, cx, bottomY, false);
        }

        private void drawAvatarWithName(Graphics g, BufferedImage img, String name, int cx, int cy, boolean nameBelow) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                int boxW = (int)(getWidth() * 0.22);
                int drawW, drawH;
                if (img != null) {
                    drawW = boxW;
                    drawH = img.getHeight() * drawW / img.getWidth();
                    g2.drawImage(img, cx - drawW/2, cy - drawH/2, drawW, drawH, null);
                } else {
                    drawW = boxW; drawH = (int)(boxW * 0.75);
                    g2.setColor(new Color(255,255,255,180));
                    g2.fillRoundRect(cx - drawW/2, cy - drawH/2, drawW, drawH, 20, 20);
                    g2.setColor(Color.BLACK);
                    g2.drawString("IMG", cx - 12, cy + 4);
                }

                g2.setFont(g2.getFont().deriveFont(18f));
                FontMetrics fm = g2.getFontMetrics();
                int nameW = fm.stringWidth(name);
                int textY = nameBelow ? (cy + drawH/2 + fm.getAscent() + 6)
                                      : (cy - drawH/2 - 6);
                g2.setColor(Color.WHITE);
                g2.drawString(name, cx - nameW/2, textY);
            } finally {
                g2.dispose();
            }
        }
    }

    // ===== 별도 채팅창 (모델리스) =====
    class ChatDialog extends JDialog {
        private final JTextArea area = new JTextArea();
        private final JTextField input = new JTextField();
        private final JButton sendBtn = new JButton("Send");

        ChatDialog() {
            super(GameRoomFrame.this, "Chat", false); // modeless
            setSize(520, 400);
            setLocationRelativeTo(GameRoomFrame.this);
            setLayout(new BorderLayout(6,6));
            setDefaultCloseOperation(HIDE_ON_CLOSE);

            area.setEditable(false);
            add(new JScrollPane(area), BorderLayout.CENTER);

            JPanel bottom = new JPanel(new BorderLayout(6,6));
            bottom.add(input, BorderLayout.CENTER);
            bottom.add(sendBtn, BorderLayout.EAST);
            add(bottom, BorderLayout.SOUTH);

            // Enter/Send → 전송
            input.addActionListener(e -> doSend());
            sendBtn.addActionListener(e -> doSend());
        }

        private void doSend() {
            String txt = input.getText();
            if (txt != null && !txt.trim().isEmpty()) {
                // 1) 내 화면에 즉시 반영(로컬 에코)
                append(myName + ": " + txt.trim());
                chatLog.append(myName).append(": ").append(txt.trim()).append('\n');

                // 2) 서버로 전송 → 상대에게 브로드캐스트
                net.send("CHAT " + txt.trim());

                input.setText("");
            }
        }

        void append(String line) {
            area.append(line + "\n");
            area.setCaretPosition(area.getDocument().getLength());
        }

        void setAllText(String text) {
            area.setText(text);
            area.setCaretPosition(area.getDocument().getLength());
        }

        void focusInput() {
            SwingUtilities.invokeLater(() -> input.requestFocusInWindow());
        }
    }
}
