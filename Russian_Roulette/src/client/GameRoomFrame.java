package client;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

public class GameRoomFrame extends JFrame {
    private final String p1Name;
    private final String p2Name;
    private final String myName;
    private final NetworkClient net;

    private final ChatPanel chatPanel;
    private final RoomCanvas canvas;

    public GameRoomFrame(String p1Name, String p2Name, String myName, NetworkClient net) {
        super("Game Room");
        this.p1Name = p1Name;
        this.p2Name = p2Name;
        this.myName = myName;
        this.net = net;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 680);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8,8));

        // 중앙: 배경 + 플레이어 렌더링
        canvas = new RoomCanvas(p1Name, p2Name);
        add(canvas, BorderLayout.CENTER);

        // 하단: 채팅
        chatPanel = new ChatPanel(new Consumer<String>() {
            @Override public void accept(String text) {
                // 엔터/전송 시 서버로 CHAT 메시지
                if (text != null && !text.trim().isEmpty()) {
                    net.send("CHAT " + text.trim());
                }
            }
        });
        add(chatPanel, BorderLayout.SOUTH);
    }

    // NetworkClient가 사용할 수신 콜백 제공
    public Consumer<String> getLineConsumer() {
        return new Consumer<String>() {
            @Override public void accept(String line) {
                if (line.startsWith("CHAT ")) {
                    // CHAT <sender>: <message>
                    chatPanel.append(line.substring(5).trim());
                }
                // 여기서는 게임 로직 없음 — UI만 미리보기
            }
        };
    }

    // ===== 내부 클래스: 배경/플레이어 그리기 캔버스 =====
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

            // 리소스에서 이미지 로딩 (없으면 null)
            bg   = ImageLoader.load("/images/room_bg.png");
            p1img = ImageLoader.load("/images/player1.png");
            p2img = ImageLoader.load("/images/player2.png");
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            int w = getWidth(), h = getHeight();

            // 배경
            if (bg != null) {
                g.drawImage(bg, 0, 0, w, h, null);
            } else {
                // 플레이스홀더 배경
                g.setColor(new Color(40, 100, 80));
                g.fillRect(0, 0, w, h);
            }

            // P1 영역 (왼쪽)
            int leftX = (int)(w * 0.15);
            int y = (int)(h * 0.35);
            if (p1img != null) {
                int iw = p1img.getWidth(), ih = p1img.getHeight();
                int drawW = (int)(w * 0.22);
                int drawH = ih * drawW / iw;
                g.drawImage(p1img, leftX - drawW/2, y - drawH/2, drawW, drawH, null);
            } else {
                g.setColor(Color.WHITE);
                g.fillRoundRect(leftX - 80, y - 80, 160, 160, 20, 20);
                g.setColor(Color.BLACK);
                g.drawString("P1 IMG", leftX - 20, y + 4);
            }
            g.setColor(Color.WHITE);
            g.setFont(g.getFont().deriveFont(18f));
            g.drawString(p1Name == null ? "P1" : p1Name, leftX - 24, y + 120);

            // P2 영역 (오른쪽)
            int rightX = (int)(w * 0.85);
            if (p2img != null) {
                int iw = p2img.getWidth(), ih = p2img.getHeight();
                int drawW = (int)(w * 0.22);
                int drawH = ih * drawW / iw;
                g.drawImage(p2img, rightX - drawW/2, y - drawH/2, drawW, drawH, null);
            } else {
                g.setColor(Color.WHITE);
                g.fillRoundRect(rightX - 80, y - 80, 160, 160, 20, 20);
                g.setColor(Color.BLACK);
                g.drawString("P2 IMG", rightX - 20, y + 4);
            }
            g.setColor(Color.WHITE);
            g.setFont(g.getFont().deriveFont(18f));
            g.drawString(p2Name == null ? "P2" : p2Name, rightX - 24, y + 120);
        }
    }

    // ===== 내부 클래스: 채팅 패널 =====
    static class ChatPanel extends JPanel {
        private final JTextArea area = new JTextArea(6, 10);
        private final JTextField input = new JTextField();
        private final JButton sendBtn = new JButton("Send");
        private final Consumer<String> onSend;

        public ChatPanel(Consumer<String> onSend) {
            this.onSend = onSend;
            setLayout(new BorderLayout(6, 6));
            area.setEditable(false);
            add(new JScrollPane(area), BorderLayout.CENTER);

            JPanel bottom = new JPanel(new BorderLayout(6, 6));
            bottom.add(input, BorderLayout.CENTER);
            bottom.add(sendBtn, BorderLayout.EAST);
            add(bottom, BorderLayout.SOUTH);

            // Enter 전송
            input.addActionListener(new java.awt.event.ActionListener() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                    doSend();
                }
            });
            // 버튼 전송
            sendBtn.addActionListener(new java.awt.event.ActionListener() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                    doSend();
                }
            });
        }

        private void doSend() {
            String txt = input.getText();
            if (txt != null && !txt.trim().isEmpty()) {
                onSend.accept(txt);
                input.setText("");
            }
        }

        public void append(String line) {
            area.append(line + "\n");
            area.setCaretPosition(area.getDocument().getLength());
        }
    }
}
