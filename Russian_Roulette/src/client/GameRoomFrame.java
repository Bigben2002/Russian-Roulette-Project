package client;

import server.Protocol;

import javax.swing.*;
import java.awt.*;
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

    public GameRoomFrame(String p1Name, String p2Name, String myName, NetworkClient net) {
        super("Game Room");
        this.p1Name = p1Name; 
        this.p2Name = p2Name; 
        this.myName = myName; 
        this.net = net;

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(960, 640);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // === 중앙 캔버스 (배경 + 캐릭터 이미지) ===
        canvas = new RoomCanvas();
        add(canvas, BorderLayout.CENTER);

        // === 상단 패널: 유저정보 + 버튼 (우측 정렬) ===
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(true);
        topBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JLabel youLabel = new JLabel("You: " + (myName.equals(p1Name) ? "P1" : "P2") + " (" + myName + ")");
        topBar.add(youLabel, BorderLayout.WEST);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton keyBtn = new JButton("조작키");
        JButton chatBtn = new JButton("Chat");
        keyBtn.addActionListener(e -> showKeyHelp());
        chatBtn.addActionListener(e -> ensureChatDialog());
        rightButtons.add(keyBtn);
        rightButtons.add(chatBtn);
        rightButtons.setOpaque(false);

        topBar.add(rightButtons, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        net.setOnLine(getLineConsumer());
    }

    private void showKeyHelp() {
        JOptionPane.showMessageDialog(this,
                "←/→: 조준 이동\nF: 발사\n1~4: 카드 사용(추가 예정)",
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
            String payload = line.substring(Protocol.CHAT.length() + 1).trim();
            int idx = payload.indexOf(':');
            String sender = (idx >= 0) ? payload.substring(0, idx).trim() : payload;
            String msg    = (idx >= 0) ? payload.substring(idx + 1).trim() : "";
            String role   = sender.equals(p1Name) ? "P1" : (sender.equals(p2Name) ? "P2" : "?");
            boolean isMe  = sender.equals(myName);
            String display = (role.equals("?") ? "" : "[" + role + "] ")
                           + (isMe ? "[ME] " : "")
                           + sender + ": " + msg;
            ensureChatDialog();
            chatDialog.appendLine(display, true);
        }
    }

 // ====== 캔버스(배경/플레이어) ======
    class RoomCanvas extends JPanel {
        private final Image bg;
        private final Image p1Img;
        private final Image p2Img;

        RoomCanvas() {
            ImageIcon bgIcon  = ImageLoader.load("images/room_bg.png");
            ImageIcon p1Icon  = ImageLoader.load("images/player1.png");
            ImageIcon p2Icon  = ImageLoader.load("images/player2.png");
            bg    = (bgIcon == null) ? null : bgIcon.getImage();
            p1Img = (p1Icon == null) ? null : p1Icon.getImage();
            p2Img = (p2Icon == null) ? null : p2Icon.getImage();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth(), h = getHeight();

            // 배경
            if (bg != null) {
                g.drawImage(bg, 0, 0, w, h, this);
            } else {
                g.setColor(new Color(40, 40, 40));
                g.fillRect(0, 0, w, h);
            }

            // === 크기와 여백 ===
            final int imgW = 150, imgH = 150;
            final int marginTop = 40;     // 위쪽 여백
            final int marginBottom = 40;  // 아래쪽 여백

            // === 가운데 정렬 X 좌표 ===
            int centerX = (w - imgW) / 2;

            // === P2: 화면 맨 위 중앙 ===
            int p2X = centerX;
            int p2Y = marginTop;
            if (p2Img != null) g.drawImage(p2Img, p2X, p2Y, imgW, imgH, this);

            // === P1: 화면 맨 아래 중앙 ===
            int p1X = centerX;
            int p1Y = h - imgH - marginBottom;
            if (p1Img != null) g.drawImage(p1Img, p1X, p1Y, imgW, imgH, this);
        }
    }



    // ================= 채팅 다이얼로그 =================
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
