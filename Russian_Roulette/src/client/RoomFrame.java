package client;

import server.Protocol;

import javax.swing.*;
import java.awt.*;

public class RoomFrame extends JFrame {
    private final JLabel statusLabel = new JLabel("STATUS: -");
    private final JLabel p1Label = new JLabel("P1: -");
    private final JLabel p2Label = new JLabel("P2: -");
    private final NetworkClient net;

    private String p1Name = null;
    private String p2Name = null;
    private final String myName;

    // === [Req 3] Ready 버튼 및 상태 ===
    private final JButton readyButton;
    private boolean p1Ready = false;
    private boolean p2Ready = false;
    // === [Req 3] 끝 ===

    public RoomFrame(String host, int port, String name) throws Exception {
        super("Lobby - " + name);
        this.myName = name;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(520, 240);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        JPanel top = new JPanel(new GridLayout(3, 1));
        statusLabel.setFont(statusLabel.getFont().deriveFont(16f));
        p1Label.setFont(p1Label.getFont().deriveFont(14f));
        p2Label.setFont(p2Label.getFont().deriveFont(14f));
        top.add(statusLabel);
        top.add(p1Label);
        top.add(p2Label);
        add(top, BorderLayout.CENTER);

        // === [Req 3] Ready 버튼 추가 ===
        readyButton = new JButton("READY");
        readyButton.setFont(new Font("SansSerif", Font.BOLD, 24));
        readyButton.setEnabled(false); // 처음엔 비활성화
        
        readyButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                net.send(Protocol.READY);
                readyButton.setEnabled(false);
                readyButton.setText("WAITING...");
            }
        });
        add(readyButton, BorderLayout.SOUTH);
        // === [Req 3] 끝 ===

        net = new NetworkClient(s -> SwingUtilities.invokeLater(() -> onServerLine(s)));
        net.connect(host, port, name);
    }

    private void onServerLine(String line) {
        if (line == null) return;

        if (line.startsWith(Protocol.ROOM_STATUS)) {
            // [Req 3] Ready 상태 파싱 및 표시
            if (line.contains("P1_READY") || line.contains("P2_READY")) {
                String p1r = parseKV(line, "P1_READY");
                String p2r = parseKV(line, "P2_READY");
                if (p1r != null) p1Ready = "true".equals(p1r);
                if (p2r != null) p2Ready = "true".equals(p2r);
                
                statusLabel.setText("STATUS: P1 " + (p1Ready ? "READY" : "...") + " | P2 " + (p2Ready ? "READY" : "..."));
            } else {
                statusLabel.setText("STATUS: " + line.substring(Protocol.ROOM_STATUS.length()).trim());
            }

        } else if (line.startsWith(Protocol.ROOM_CREATED)) {
            p1Name = parseKV(line, "P1");
            p2Name = parseKV(line, "P2");
            p1Label.setText("P1: " + (p1Name == null ? "-" : p1Name));
            p2Label.setText("P2: " + (p2Name == null ? "-" : p2Name));

        } else if (line.startsWith(Protocol.ENTER_ROOM)) {
            // [Req 3] 게임방 즉시 입장이 아닌, Ready 버튼 활성화
            statusLabel.setText("STATUS: Press Ready to Start");
            readyButton.setEnabled(true);
            
        } else if (line.startsWith(Protocol.GAME_START)) {
            // [Req 3] GAME_START 신호를 받으면 게임방 입장
            if (p1Name == null) p1Name = parseKV(line, "P1");
            if (p2Name == null) p2Name = parseKV(line, "P2");
            
            // === [Req 3-3] 초기 총알 정보 파싱 ===
            int initialBullets = parseIntSafe(parseKV(line, "B"), 0);
            int initialBlanks  = parseIntSafe(parseKV(line, "K"), 0);
            // === [Req 3-3] 끝 ===
            
            try {
                // === [Req 3-3] 생성자에 총알 정보 전달 ===
                GameRoomFrame gf = new GameRoomFrame(p1Name, p2Name, myName, net, initialBullets, initialBlanks);
                net.setOnLine(gf.getLineConsumer());
                gf.setVisible(true);
                dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "게임방 열기 실패: " + ex.getMessage());
            }
        }
    }

    private String parseKV(String line, String key) {
        String[] sp = line.split("\\s+");
        for (String tok : sp) {
            if (tok.startsWith(key + "=")) return tok.substring((key + "=").length());
        }
        return null;
    }
    
    // [Req 3-3] GameRoomFrame에서 가져온 헬퍼 메소드
    private int parseIntSafe(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return def; }
    }
}