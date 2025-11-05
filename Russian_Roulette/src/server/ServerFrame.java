package server;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Consumer;

public class ServerFrame extends JFrame {
    private final JTextField portField = new JTextField("7777", 8);
    private final JButton startBtn = new JButton("방 만들기(서버 시작)");
    private final JButton stopBtn  = new JButton("서버 중지");
    private final JTextArea logArea = new JTextArea();

    private final ServerCore core;

    public ServerFrame() {
        super("Russian Roulette - Server");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(640, 420);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8,8));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("포트:"));
        top.add(portField);
        top.add(startBtn);
        top.add(stopBtn);
        add(top, BorderLayout.NORTH);

        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        core = new ServerCore(new Consumer<String>() {
            @Override public void accept(String s) { appendLog(s); }
        });

        startBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { onStart(); }
        });
        stopBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { onStop(); }
        });
    }

    private void onStart() {
        final int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1024 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "포트는 1024~65535 사이의 숫자여야 합니다.");
            return;
        }
        try {
            core.start(port);
            appendLog("[UI] 방 만들기 완료. 접속을 기다립니다... (2명 모이면 READY)");
            startBtn.setEnabled(false);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "서버 시작 실패: " + ex.getMessage());
        }
    }

    private void onStop() {
        core.stop();
        startBtn.setEnabled(true);
    }

    private void appendLog(String s) {
        logArea.append(s + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}
