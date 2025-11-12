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

        net = new NetworkClient(s -> SwingUtilities.invokeLater(() -> onServerLine(s)));
        net.connect(host, port, name);
    }

    private void onServerLine(String line) {
        if (line == null) return;

        if (line.startsWith(Protocol.ROOM_STATUS)) {
            statusLabel.setText("STATUS: " + line.substring(Protocol.ROOM_STATUS.length()).trim());

        } else if (line.startsWith(Protocol.ROOM_CREATED)) {
            p1Name = parseKV(line, "P1");
            p2Name = parseKV(line, "P2");
            p1Label.setText("P1: " + (p1Name == null ? "-" : p1Name));
            p2Label.setText("P2: " + (p2Name == null ? "-" : p2Name));

        } else if (line.startsWith(Protocol.ENTER_ROOM)) {
            if (p1Name == null) p1Name = parseKV(line, "P1");
            if (p2Name == null) p2Name = parseKV(line, "P2");
            try {
                GameRoomFrame gf = new GameRoomFrame(p1Name, p2Name, myName, net);
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
}
