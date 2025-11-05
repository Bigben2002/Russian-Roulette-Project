package server;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final Room room;
    private final String name;
    private final BufferedReader in;
    private final BufferedWriter out;

    public ClientHandler(Socket socket, Room room, String name) throws IOException {
        this.socket = socket;
        this.room = room;
        this.name = name;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
    }

    public void send(String line) throws IOException {
        out.write(line);
        out.write("\n");
        out.flush();
    }

    @Override
    public void run() {
        try {
            // 로비 단계는 수신 처리 필요 없음 (향후: READY, LEAVE 등 추가 가능)
            while (in.readLine() != null) {
                // ignore for now
            }
        } catch (IOException ignored) {
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
