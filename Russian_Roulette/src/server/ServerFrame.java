package server;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URL;

public class ServerFrame extends JFrame {
    private final JTextField portField = new JTextField("7777", 8);
    // [수정] JTextArea의 높이를 4로 최소화 (rows = 4, cols = 30)
    private final JTextArea logArea = new JTextArea(4, 30);
    private ServerCore core;

    public ServerFrame() {
        super("Server GUI");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // ----------------- 배경 이미지 로딩 -----------------
        final Image bgImage;
        Image tempImage = null;
        try {
            URL url = getClass().getClassLoader().getResource("images/gameStartBG.png");
            if (url != null) {
                tempImage = new ImageIcon(url).getImage();
            }
        } catch (Exception ignored) {}
        bgImage = tempImage;

        // ----------------- 레이어드 패널 기본 설정 -----------------
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setPreferredSize(new Dimension(800, 600));
        this.setContentPane(layeredPane);

        // ----------------- 배경 패널 -----------------
        JPanel bgPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (bgImage != null) {
                    g.drawImage(bgImage, 0, 0, getWidth(), getHeight(), this);
                } else {
                    g.setColor(new Color(40, 40, 40));
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            }
        };
        bgPanel.setBounds(0, 0, 800, 600);
        layeredPane.add(bgPanel, JLayeredPane.DEFAULT_LAYER);

        // ----------------- UI 패널 (반투명 오버레이) -----------------
        JPanel uiPanel = new JPanel(new BorderLayout(10, 10));
        uiPanel.setOpaque(false);
        uiPanel.setBounds(50, 20, 700, 560);   // 전체 UI 영역
        layeredPane.add(uiPanel, JLayeredPane.PALETTE_LAYER);

        // --- 3. 상단 배치 (포트, Game Start, Game End 버튼) ---
        JPanel north = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
        north.setOpaque(false);

        JLabel portLabel = new JLabel("Port:");
        portLabel.setForeground(Color.WHITE);
        portField.setBackground(new Color(50, 50, 50));
        portField.setForeground(Color.WHITE);

        JButton startBtn = new JButton("Game Start");
        JButton stopBtn  = new JButton("Game End");

        styleButton(startBtn, new Color(50, 150, 50));
        styleButton(stopBtn, new Color(150, 50, 50));

        north.add(portLabel);
        north.add(portField);
        north.add(startBtn);
        north.add(stopBtn);

        uiPanel.add(north, BorderLayout.NORTH);

        // --- 4. 하단 배치 (로그 영역) ---
        logArea.setEditable(false);
        logArea.setBackground(new Color(40, 40, 40));
        logArea.setForeground(Color.WHITE);
        logArea.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setOpaque(true);
        scrollPane.getViewport().setOpaque(true);
        // 높이만 얇게 보이도록 선호 사이즈 지정
        scrollPane.setPreferredSize(new Dimension(0, 100));  // 높이 약 100px 정도
        uiPanel.add(scrollPane, BorderLayout.SOUTH);

        // 버튼 이벤트
        startBtn.addActionListener(e -> startServer());
        stopBtn.addActionListener(e -> stopServer());

        logArea.append("[System] Server UI Initialized. Waiting for Start.\n");

        pack();
        setLocationRelativeTo(null);
    }

    // 버튼 스타일 공통 적용
    private void styleButton(JButton btn, Color bgColor) {
        btn.setFont(new Font("SansSerif", Font.BOLD, 18));
        btn.setBackground(bgColor);
        btn.setForeground(Color.WHITE);
        btn.setFocusable(false);
        btn.setBorder(BorderFactory.createRaisedBevelBorder());
    }

    private void startServer() {
        try {
            int port = Integer.parseInt(portField.getText().trim());
            core = new ServerCore(msg ->
                    SwingUtilities.invokeLater(() -> logArea.append(msg + "\n")));
            core.start(port);
            logArea.append("[UI] Server started on " + port + "\n");
        } catch (IOException e) {
            logArea.append("[UI] Start failed: " + e.getMessage() + "\n");
            JOptionPane.showMessageDialog(this, "Start failed: " + e.getMessage());
        } catch (NumberFormatException e) {
            logArea.append("[UI] Invalid Port number.\n");
            JOptionPane.showMessageDialog(this, "포트 번호를 숫자로 입력해 주세요.");
        }
    }

    private void stopServer() {
        try {
            if (core != null) core.stop();
            logArea.append("[UI] Server stopped.\n");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Stop failed: " + ex.getMessage());
        }
    }
}
