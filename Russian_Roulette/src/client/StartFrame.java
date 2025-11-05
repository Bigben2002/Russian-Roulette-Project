package client;

import javax.swing.*;
import java.awt.*;

public class StartFrame extends JFrame {
    private final JTextField hostField = new JTextField("127.0.0.1", 14);
    private final JTextField portField = new JTextField("7777", 6);
    private final JTextField nameField = new JTextField("Player", 10);
    private final JButton connectBtn = new JButton("Start / Connect");

    public StartFrame() {
        super("Russian Roulette - Start");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(360, 180);
        setLocationRelativeTo(null);
        setLayout(new GridLayout(4, 2, 6, 6));

        add(new JLabel("Host:"));
        add(hostField);
        add(new JLabel("Port:"));
        add(portField);
        add(new JLabel("Name:"));
        add(nameField);
        add(new JLabel());
        add(connectBtn);

        connectBtn.addActionListener(e -> connect());
    }

    private void connect() {
        String host = hostField.getText().trim();
        int port = Integer.parseInt(portField.getText().trim());
        String name = nameField.getText().trim();
        try {
            RoomFrame rf = new RoomFrame(host, port, name);
            rf.setVisible(true);
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Connect failed: " + ex.getMessage());
        }
    }
}
