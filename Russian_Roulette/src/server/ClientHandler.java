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
            String line;
            while ((line = in.readLine()) != null) {
                // CHAT <message...>
                if (line.startsWith("CHAT ")) {
                    String msg = line.substring(5).trim();
                    if (room != null && !msg.isEmpty()) {
                        room.broadcastChat(name, msg);
                    }
                }
                // 로비 단계의 다른 명령은 없음
            }
        } catch (IOException ignored) {
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
