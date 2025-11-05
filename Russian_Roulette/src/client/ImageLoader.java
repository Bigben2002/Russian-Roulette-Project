package client;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

public class ImageLoader {
    public static BufferedImage load(String pathInResources) {
        if (pathInResources == null || !pathInResources.startsWith("/")) {
            throw new IllegalArgumentException("경로는 반드시 '/'로 시작해야 함: " + pathInResources);
        }

        try (InputStream in = ImageLoader.class.getResourceAsStream(pathInResources)) {
            if (in == null) {
                throw new IllegalArgumentException("리소스 없음: " + pathInResources);
            }
            return ImageIO.read(in);
        } catch (Exception e) {
            throw new RuntimeException("이미지 로드 실패: " + pathInResources, e);
        }
    }
}
