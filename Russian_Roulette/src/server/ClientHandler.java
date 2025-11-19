package server;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out; // autoFlush = true

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
                
                // [안전성 강화] 모든 라인에서 앞뒤 공백 제거
                String trimmedLine = line.trim();

                // === CHAT <text> ===
                if (trimmedLine.startsWith(Protocol.CHAT + " ")) {
                    String msg = trimmedLine.substring(Protocol.CHAT.length() + 1).trim();
                    if (room != null && !msg.isEmpty()) room.broadcastChat(nickname, msg);
                    continue;
                }

                // === [Req 3] READY ===
                if (trimmedLine.equals(Protocol.READY)) {
                    if (room != null) room.onReady(this);
                    continue;
                }

                // === AIM SELF|ENEMY ===
                if (trimmedLine.startsWith(Protocol.AIM + " ")) {
                    String target = trimmedLine.substring(Protocol.AIM.length() + 1).trim();
                    if (room != null) room.onAim(this, target);
                    continue;
                }

                // === FIRE ===
                if (trimmedLine.equals(Protocol.FIRE)) {
                    if (room != null) room.onFire(this);
                    continue;
                }
                
                // === [Item] USE_ITEM SLOT=1..6 ===
                // [수정] 파싱 로직을 강화하여 "USE_ITEM SLOT=" 패턴을 확인합니다.
                if (trimmedLine.startsWith(Protocol.USE_ITEM)) {
                    try {
                        // "USE_ITEM" 다음 부분만 파싱하여 공백을 제거하고 SLOT=X 형식을 찾습니다.
                        String param = trimmedLine.substring(Protocol.USE_ITEM.length()).trim();
                        if (param.startsWith("SLOT=")) {
                            String slotStr = param.substring("SLOT=".length()).trim();
                            int slot = Integer.parseInt(slotStr);
                            if (room != null) room.onUseItem(this, slot);
                        }
                    } catch (NumberFormatException ignored) {
                        // 숫자가 아니거나 파싱 실패는 무시
                    }
                    continue; 
                }

                // === [치명적 오류 방지] 알 수 없는 명령이 들어와도 다음 라인을 읽도록 처리 ===
                // System.out.println("DEBUG: Unknown command received: " + trimmedLine);
            }
        } catch (IOException ignore) {
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    public void send(String line) { out.println(line); }
}