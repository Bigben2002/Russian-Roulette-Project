package client;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

public class ImageLoader {
    public static BufferedImage load(String pathInResources) {
        // 예: "/images/room_bg.png"
        try (InputStream in = ImageLoader.class.getResourceAsStream(pathInResources)) {
            if (in == null) return null;
            return ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }
}
