package client;

import javax.swing.*;
import java.awt.*;

public class StartFrame extends JFrame {
    private final JTextField hostField = new JTextField("127.0.0.1", 12);
    private final JTextField portField = new JTextField("7777", 6);
    private final JTextField nameField = new JTextField("Player", 10);

    public StartFrame() {
        super("Client Start");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8,8));

        JPanel p = new JPanel(new GridLayout(3,2,8,8));
        p.add(new JLabel("Host:")); p.add(hostField);
        p.add(new JLabel("Port:")); p.add(portField);
        p.add(new JLabel("Name:")); p.add(nameField);
        add(p, BorderLayout.CENTER);

        JButton btn = new JButton("Connect");
        add(btn, BorderLayout.SOUTH);

        btn.addActionListener(e -> connect());
        pack();
        setLocationRelativeTo(null);
    }

    private void connect() {
        try {
            String host = hostField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());
            String name = nameField.getText().trim();
            RoomFrame rf = new RoomFrame(host, port, name);
            rf.setVisible(true);
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Connect failed: " + ex.getMessage());
        }
    }
}
