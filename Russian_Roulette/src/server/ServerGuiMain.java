package server;

import javax.swing.*;

public class ServerGuiMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ServerFrame().setVisible(true));
    }
}
