package client;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.function.Consumer;

public class NetworkClient {
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;

    private Consumer<String> onLine;

    public NetworkClient(Consumer<String> onLine) {
        this.onLine = onLine;
    }

    public void setOnLine(Consumer<String> newListener) {
        this.onLine = newListener;
    }

    public void connect(String host, int port, String name) throws IOException {
        SocketAddress addr = new InetSocketAddress(host, port);
        socket = new Socket();
        socket.connect(addr, 3000);
        socket.setSoTimeout(0);

        in  = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));

        String hello = in.readLine();
        if (hello == null || !hello.startsWith("HELLO")) {
            throw new IOException("Handshake failed: HELLO not received");
        }
        out.write(name);
        out.write("\n");
        out.flush();

        Thread reader = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        final String msg = line;
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override public void run() {
                                if (onLine != null) onLine.accept(msg);
                            }
                        });
                    }
                } catch (IOException ignored) {
                } finally {
                    try { socket.close(); } catch (IOException ignored) {}
                }
            }
        }, "Client-Reader");
        reader.setDaemon(true);
        reader.start();
    }

    public void send(String line) {
        try {
            out.write(line);
            out.write("\n");
            out.flush();
        } catch (IOException ignored) {}
    }
}
