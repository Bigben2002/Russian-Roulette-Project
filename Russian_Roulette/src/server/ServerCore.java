package server;

import javax.swing.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

public class ServerCore {
    private final Consumer<String> log; // GUI 로그 콜백
    private ServerSocket server;
    private volatile boolean running = false;
    private Thread acceptThread;

    public ServerCore(Consumer<String> logger) {
        this.log = logger;
    }

    public synchronized void start(int port) throws IOException {
        if (running) { log.accept("[Server] already running"); return; }
        server = new ServerSocket(port);
        running = true;
        log.accept("[Server] Listening on " + port);

        acceptThread = new Thread(this::acceptLoop, "AcceptLoop");
        acceptThread.start();
    }

    public synchronized void stop() {
        running = false;
        try {
            if (server != null && !server.isClosed()) server.close();
        } catch (IOException ignored) {}
        log.accept("[Server] Stopped.");
    }

    private void acceptLoop() {
        try {
            while (running) {
                // 첫 번째 클라이언트
                Socket s1 = server.accept();
                String n1 = handshakeAndReadName(s1);
                log.accept("[Server] P1 connected: " + n1 + " from " + s1.getRemoteSocketAddress());
                sendLine(s1, Protocol.ROOM_STATUS + " WAITING 1/2");

                // 두 번째 클라이언트
                Socket s2 = server.accept();
                String n2 = handshakeAndReadName(s2);
                log.accept("[Server] P2 connected: " + n2 + " from " + s2.getRemoteSocketAddress());
                sendLine(s2, Protocol.ROOM_STATUS + " WAITING 2/2");

                // 핸들러 생성
                ClientHandler p1 = new ClientHandler(s1, n1);
                ClientHandler p2 = new ClientHandler(s2, n2);
                Room room = new Room(p1, p2, n1, n2);

                // 핸들러 스레드 시작
                new Thread(p1, "P1-Handler").start();
                new Thread(p2, "P2-Handler").start();

                // 룸 준비 방송
                room.announceCreatedAndReady();
                log.accept("[Server] Room READY: " + n1 + " vs " + n2);
            }
        } catch (IOException e) {
            if (running) log.accept("[Server] accept error: " + e.getMessage());
        } finally {
            stop();
        }
    }

    private static String handshakeAndReadName(Socket s) throws IOException {
        BufferedReader in  = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"));
        out.write(Protocol.HELLO + "\n"); out.flush();
        String name = in.readLine();
        return (name == null || name.isBlank()) ? "Player" : name.trim();
    }

    private static void sendLine(Socket s, String line) throws IOException {
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"));
        out.write(line); out.write("\n"); out.flush();
    }
}
