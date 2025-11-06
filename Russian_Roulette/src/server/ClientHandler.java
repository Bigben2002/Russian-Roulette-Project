package server;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out; // autoFlush
    private volatile Room room;
    private final String nickname;

    public ClientHandler(Socket socket, String nickname) throws IOException {
        this.socket   = socket;
        this.nickname = nickname;
        this.in  = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
    }

    public void setRoom(Room room) { this.room = room; }
    public String getNickname() { return nickname; }

    @Override
    public void run() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) continue;

                // === CHAT <msg> ===
                if (line.startsWith(Protocol.CHAT + " ")) {
                    String msg = line.substring(Protocol.CHAT.length() + 1).trim();
                    if (room != null && !msg.isEmpty()) {
                        room.broadcastChat(nickname, msg);
                    }
                    continue;
                }

                // (확장 지점) 그 외 프로토콜: TURN/FIRE/ITEM 등
            }
        } catch (IOException ignore) {
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    public void send(String line) {
        out.println(line); // PrintWriter autoFlush
    }
}
