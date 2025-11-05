package server;

public class ServerGuiMain {
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                new ServerFrame().setVisible(true);
            }
        });
    }
}
