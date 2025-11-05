package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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

        connectBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { connect(); }
        });
    }

    private void connect() {
        String host = hostField.getText().trim();
        String portText = portField.getText().trim();
        String name = nameField.getText().trim();

        if (host.isEmpty() || portText.isEmpty() || name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Host/Port/Name을 모두 입력하세요.");
            return;
        }
        final int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Port는 숫자여야 합니다.");
            return;
        }

        try {
            RoomFrame rf = new RoomFrame(host, port, name);
            rf.setVisible(true);
            dispose();
        } catch (java.net.ConnectException ex) {
            JOptionPane.showMessageDialog(this,
                "서버에 연결할 수 없습니다.\n" +
                "- 서버가 GUI로 실행되어 Listening 중인지\n" +
                "- Host/IP와 Port가 맞는지 확인하세요.\n\n" +
                "원인: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
        } catch (java.net.UnknownHostException ex) {
            JOptionPane.showMessageDialog(this,
                "호스트를 해석할 수 없습니다. Host에 IP를 사용해 보세요.\n\n" +
                "원인: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
        } catch (java.net.SocketTimeoutException ex) {
            JOptionPane.showMessageDialog(this,
                "서버 연결 시간 초과입니다. 같은 PC면 127.0.0.1로 시도하세요.\n\n" +
                "원인: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Connect failed (상세): " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
        }
    }
}
