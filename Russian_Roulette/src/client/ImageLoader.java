package client;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

public final class ImageLoader {
    private ImageLoader(){}

    public static ImageIcon load(String path) {
        // 리소스는 classpath 기준: resources/images/...
        URL url = ImageLoader.class.getClassLoader().getResource(path);
        if (url == null) return null;
        return new ImageIcon(url);
    }

    public static Image scaled(ImageIcon icon, int w, int h) {
        if (icon == null) return null;
        return icon.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
    }
}