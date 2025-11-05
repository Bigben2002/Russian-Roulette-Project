package client;

import javax.swing.*;
import java.awt.*;

public class RoomFrame extends JFrame {
    private final JLabel statusLabel = new JLabel("STATUS: -");
    private final JLabel p1Label = new JLabel("P1: -");
    private final JLabel p2Label = new JLabel("P2: -");
    private final JButton startButton = new JButton("Start Game (Disabled)");
    private final NetworkClient net;

    public RoomFrame(String host, int port, String name) throws Exception {
        super("Lobby - " + name);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(520, 240);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        JPanel top = new JPanel(new GridLayout(3,1));
        statusLabel.setFont(statusLabel.getFont().deriveFont(16f));
        p1Label.setFont(p1Label.getFont().deriveFont(14f));
        p2Label.setFont(p2Label.getFont().deriveFont(14f));
        top.add(statusLabel);
        top.add(p1Label);
        top.add(p2Label);

        startButton.setEnabled(false); // 지금은 게임 시작 안 함
        add(top, BorderLayout.CENTER);
        add(startButton, BorderLayout.SOUTH);

        net = new NetworkClient(this::onServerLine);
        net.connect(host, port, name);
    }

    private void onServerLine(String line) {
        if (line.startsWith("ROOM_STATUS")) {
            // ROOM_STATUS WAITING 1/2  |  ROOM_STATUS READY 2/2
            statusLabel.setText("STATUS: " + line.substring("ROOM_STATUS ".length()).trim());
            if (line.contains("READY")) {
                startButton.setText("Both connected (Game will start later)");
                startButton.setEnabled(false); // 의도적으로 비활성화 유지
                setTitle("Lobby - READY");
            }
        } else if (line.startsWith("ROOM_CREATED")) {
            // ROOM_CREATED P1=<name> P2=<name>
            String p1 = parseKV(line, "P1");
            String p2 = parseKV(line, "P2");
            p1Label.setText("P1: " + (p1 == null ? "-" : p1));
            p2Label.setText("P2: " + (p2 == null ? "-" : p2));
        }
    }

    private String parseKV(String line, String key) {
        String[] sp = line.split("\\s+");
        for (String tok : sp) {
            if (tok.startsWith(key + "=")) {
                return tok.substring((key + "=").length());
            }
        }
        return null;
    }
}
