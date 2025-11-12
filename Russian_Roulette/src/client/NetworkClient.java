package client;

import server.Protocol;
import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class NetworkClient {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private volatile Consumer<String> onLine = s -> {};

    public NetworkClient(Consumer<String> initialConsumer) {
        if (initialConsumer != null) this.onLine = initialConsumer;
    }

    public void connect(String host, int port, String name) throws IOException {
        socket = new Socket(host, port);
        in  = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

        // 서버 HELLO 응답(닉네임 전송)
        String hello = in.readLine();
        if (Protocol.HELLO.equals(hello)) { out.println(name); }

        new Thread(this::listen, "ClientListen").start();
    }

    private void listen() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                onLine.accept(line);
            }
        } catch (IOException ignore) {
        }
    }

    public void setOnLine(Consumer<String> consumer) {
        this.onLine = (consumer == null) ? (s -> {}) : consumer;
    }

    public void send(String line) {
        out.println(line);
    }
}
