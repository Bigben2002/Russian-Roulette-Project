package client;

import server.Protocol;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class RoomFrame extends JFrame {
    private final JLabel statusLabel = new JLabel("STATUS: -");
    private final JLabel p1Label = new JLabel("P1: -");
    private final JLabel p2Label = new JLabel("P2: -");
    private final NetworkClient net;

    private String p1Name = null;
    private String p2Name = null;
    private final String myName;

    // === Ready 버튼 및 상태 ===
    private final JButton readyButton;
    private boolean p1Ready = false;
    private boolean p2Ready = false;
    // === 끝 ===

    public RoomFrame(String host, int port, String name) throws Exception {
        super("Lobby - " + name);
        this.myName = name;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(520, 240);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        
        // NetClient 초기화
        net = new NetworkClient(this::handleServerLine);
        
        try {
            net.connect(host, port, name);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "서버 연결 실패: " + e.getMessage());
            throw new IOException("Connection failed", e);
        }

        JPanel top = new JPanel(new GridLayout(3, 1));
        statusLabel.setFont(statusLabel.getFont().deriveFont(16f));
        p1Label.setFont(p1Label.getFont().deriveFont(14f));
        p2Label.setFont(p2Label.getFont().deriveFont(14f));
        top.add(statusLabel);
        top.add(p1Label);
        top.add(p2Label);
        
        // Ready 버튼
        readyButton = new JButton("READY");
        readyButton.setFont(new Font("SansSerif", Font.BOLD, 24));
        readyButton.setEnabled(false); // 처음엔 비활성화
        
        readyButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                // 서버는 Protocol.READY만 받음
                net.send(Protocol.READY);
                readyButton.setEnabled(false);
                readyButton.setText("WAITING...");
            }
        });
        
        add(top, BorderLayout.CENTER);
        add(readyButton, BorderLayout.SOUTH);
    }

    private void handleServerLine(String line) {
        if (line == null) return;
        
        // ROOM_STATUS WAITING [1/2]
        if (line.startsWith(Protocol.ROOM_STATUS + " ")) {
            // ROOM_STATUS는 대기 상태(1/2) 또는 레디 상태(P1_READY=...)를 모두 포함할 수 있음
            String p1r = parseKV(line, "P1_READY");
            String p2r = parseKV(line, "P2_READY");
            if (p1r != null) p1Ready = "true".equals(p1r);
            if (p2r != null) p2Ready = "true".equals(p2r);
            
            if (p1r != null && p2r != null) {
                statusLabel.setText("STATUS: P1 " + (p1Ready ? "READY" : "...") + " | P2 " + (p2Ready ? "READY" : "..."));
            } else {
                 statusLabel.setText("STATUS: " + line.substring(Protocol.ROOM_STATUS.length()).trim());
            }
            return;
        }

        // ROOM_CREATED P1=[name1] P2=[name2]
        if (line.startsWith(Protocol.ROOM_CREATED + " ")) {
            p1Name = parseKV(line, "P1");
            p2Name = parseKV(line, "P2");
            p1Label.setText("P1: " + (p1Name == null ? "-" : p1Name));
            p2Label.setText("P2: " + (p2Name == null ? "-" : p2Name));
            
            return;
        }
        
        // ENTER_ROOM P1=[name1] P2=[name2]
        if (line.startsWith(Protocol.ENTER_ROOM)) {
            // 게임방 즉시 입장이 아닌, Ready 버튼 활성화
            statusLabel.setText("STATUS: Press Ready to Start");
            readyButton.setEnabled(true);
            
        } 
        
        // READY_STATE P1_READY=true P2_READY=false  <--- 새로운 프로토콜에 맞춤
        if (line.startsWith(Protocol.READY_STATE + " ")) {
            p1Ready = "true".equals(parseKV(line, "P1_READY"));
            p2Ready = "true".equals(parseKV(line, "P2_READY"));
            
            p1Label.setText("P1: " + p1Name + (p1Ready ? " (Ready)" : ""));
            p2Label.setText("P2: " + p2Name + (p2Ready ? " (Ready)" : ""));
            
            statusLabel.setText("STATUS: P1 " + (p1Ready ? "READY" : "...") + " | P2 " + (p2Ready ? "READY" : "..."));
            return;
        }

        // GAME_START P1=... P2=... B=k K=k P1_ITEMS=a,b,c P2_ITEMS=d,e,f
        if (line.startsWith(Protocol.GAME_START + " ")) {
            if (p1Name == null) p1Name = parseKV(line, "P1");
            if (p2Name == null) p2Name = parseKV(line, "P2");
            
            // 초기 총알 정보 파싱
            String initialBullets = parseKV(line, "B");
            String initialBlanks  = parseKV(line, "K");
            
            // 초기 아이템 목록을 파싱
            String p1InitialItems = parseKV(line, "P1_ITEMS");
            String p2InitialItems = parseKV(line, "P2_ITEMS");
            
            try {
                GameRoomFrame gf = new GameRoomFrame(p1Name, p2Name, myName, net, 
                                                     parseIntSafe(initialBullets, 0), 
                                                     parseIntSafe(initialBlanks, 0),
                                                     p1InitialItems, p2InitialItems); 
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
    
    private int parseIntSafe(String s, int defaultValue) {
        if (s == null) return defaultValue;
        try { 
            return Integer.parseInt(s.trim()); 
        }
        catch (Exception e) { 
            return defaultValue; 
        }
    }
}