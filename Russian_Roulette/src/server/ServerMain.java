package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java server.ServerMain <port>");
            return;
        }
        int port = Integer.parseInt(args[0]);

        System.out.println("[Server] Listening on " + port);

        try (ServerSocket ss = new ServerSocket(port)) {
            // 1명 대기
            Socket s1 = ss.accept();
            String n1 = handshakeAndReadName(s1);
            System.out.println("[Server] P1 connected: " + n1);
            sendLine(s1, Protocol.ROOM_STATUS + " WAITING 1/2");

            // 2명 대기
            Socket s2 = ss.accept();
            String n2 = handshakeAndReadName(s2);
            System.out.println("[Server] P2 connected: " + n2);
            sendLine(s2, Protocol.ROOM_STATUS + " WAITING 2/2");

            // 룸, 핸들러 구성
            Room room = new Room(
                    new ClientHandler(s1, null, n1), // 임시
                    new ClientHandler(s2, null, n2), // 임시
                    n1, n2
            );
            // 실제 room 참조를 가진 핸들러 재생성
            ClientHandler p1 = new ClientHandler(s1, room, n1);
            ClientHandler p2 = new ClientHandler(s2, room, n2);

            // 스레드 시작
            Thread t1 = new Thread(p1, "P1-Handler");
            Thread t2 = new Thread(p2, "P2-Handler");
            t1.start();
            t2.start();

            // 룸 생성 & READY 알림 (게임 시작은 하지 않음)
            room = new Room(p1, p2, n1, n2);
            room.announceCreatedAndReady();

            // 블로킹 대기 (서버 유지)
            t1.join();
            t2.join();
            System.out.println("[Server] Room closed.");
        }
    }

    private static String handshakeAndReadName(Socket s) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"));
        out.write(Protocol.HELLO + "\n");
        out.flush();
        String name = in.readLine();
        return (name == null || name.isBlank()) ? "Player" : name.trim();
    }

    private static void sendLine(Socket s, String line) throws IOException {
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"));
        out.write(line);
        out.write("\n");
        out.flush();
    }
}
