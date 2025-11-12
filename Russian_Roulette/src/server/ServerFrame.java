package server;

import javax.swing.*;
import java.awt.*;

public class ServerFrame extends JFrame {
    private final JTextField portField = new JTextField("7777", 8);
    private final JTextArea logArea = new JTextArea(18, 60);
    private ServerCore core;

    public ServerFrame() {
        super("Server");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8,8));

        JPanel north = new JPanel();
        north.add(new JLabel("Port:"));
        north.add(portField);
        JButton startBtn = new JButton("Start");
        JButton stopBtn  = new JButton("Stop");
        north.add(startBtn);
        north.add(stopBtn);
        add(north, BorderLayout.NORTH);

        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        startBtn.addActionListener(e -> startServer());
        stopBtn.addActionListener(e -> stopServer());

        pack();
        setLocationRelativeTo(null);
    }

    private void startServer() {
        try {
            int port = Integer.parseInt(portField.getText().trim());
            core = new ServerCore(msg -> SwingUtilities.invokeLater(() -> logArea.append(msg + "\n")));
            core.start(port);
            logArea.append("[UI] Server started on " + port + "\n");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Start failed: " + ex.getMessage());
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
