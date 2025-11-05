package client;

import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class NetworkClient {
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;

    private final Consumer<String> onLine;

    public NetworkClient(Consumer<String> onLine) {
        this.onLine = onLine;
    }

    public void connect(String host, int port, String name) throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));

        // 서버 HELLO
        String hello = in.readLine();
        // 이름 전송
        out.write(name + "\n");
        out.flush();

        Thread reader = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    // 🔧 람다에서 사용할 값은 final 변수로 복사
                    final String msg = line;
                    SwingUtilities.invokeLater(() -> onLine.accept(msg));
                }
            } catch (IOException ignored) {
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }, "Client-Reader");
        reader.setDaemon(true);
        reader.start();
    }

    // 향후: READY, LEAVE 등 확장을 위해 send 제공
    public void send(String line) {
        try {
            out.write(line);
            out.write("\n");
            out.flush();
        } catch (IOException ignored) {}
    }
}
