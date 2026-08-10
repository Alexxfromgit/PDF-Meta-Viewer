import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the repository-native application artwork without third-party tooling.
 * Run with Java's source-file launcher; this class is not part of the application JAR.
 */
public final class IconGenerator {
    private static final Color ACCENT = new Color(206, 62, 67);
    private static final int[] ICO_SIZES = {16, 24, 32, 48, 64, 128, 256};
    private static final int[] ICNS_SIZES = {16, 32, 64, 128, 256, 512, 1024};
    private static final String[] ICNS_TYPES = {"icp4", "icp5", "icp6", "ic07", "ic08", "ic09", "ic10"};

    private IconGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException(
                    "Usage: IconGenerator.java png <output> <size> | ico <output> | icns <output>");
        }

        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        Path parent = output.getParent();
        if (parent != null && !Files.isDirectory(parent)) {
            Files.createDirectories(parent);
        }

        switch (args[0]) {
            case "png" -> {
                if (args.length != 3) {
                    throw new IllegalArgumentException("PNG output requires a pixel size");
                }
                int size = Integer.parseInt(args[2]);
                if (size < 1 || size > 4096) {
                    throw new IllegalArgumentException("PNG size must be between 1 and 4096");
                }
                ImageIO.write(createIcon(size), "png", output.toFile());
            }
            case "ico" -> writeIco(output);
            case "icns" -> writeIcns(output);
            default -> throw new IllegalArgumentException("Output type must be png, ico, or icns");
        }
    }

    private static BufferedImage createIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(ACCENT);
            graphics.fillRoundRect(size / 8, size / 12, size * 3 / 4, size * 5 / 6,
                    size / 5, size / 5);
            graphics.setColor(new Color(255, 255, 255, 230));
            graphics.fillRoundRect(size * 3 / 10, size * 3 / 10, size * 2 / 5, size / 12,
                    Math.max(1, size / 24), Math.max(1, size / 24));
            graphics.fillRoundRect(size * 3 / 10, size * 47 / 100, size * 2 / 5, size / 12,
                    Math.max(1, size / 24), Math.max(1, size / 24));
            graphics.fillRoundRect(size * 3 / 10, size * 64 / 100, size / 4, size / 12,
                    Math.max(1, size / 24), Math.max(1, size / 24));
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void writeIco(Path output) throws IOException {
        List<byte[]> images = createPngImages(ICO_SIZES);

        try (DataOutputStream stream = new DataOutputStream(Files.newOutputStream(output))) {
            writeLittleEndianShort(stream, 0);
            writeLittleEndianShort(stream, 1);
            writeLittleEndianShort(stream, images.size());

            int imageOffset = 6 + (16 * images.size());
            for (int index = 0; index < images.size(); index++) {
                int size = ICO_SIZES[index];
                byte[] image = images.get(index);
                stream.writeByte(size == 256 ? 0 : size);
                stream.writeByte(size == 256 ? 0 : size);
                stream.writeByte(0);
                stream.writeByte(0);
                writeLittleEndianShort(stream, 1);
                writeLittleEndianShort(stream, 32);
                writeLittleEndianInt(stream, image.length);
                writeLittleEndianInt(stream, imageOffset);
                imageOffset += image.length;
            }

            for (byte[] image : images) {
                stream.write(image);
            }
        }
    }

    private static void writeIcns(Path output) throws IOException {
        List<byte[]> images = createPngImages(ICNS_SIZES);
        int totalLength = 8;
        for (byte[] image : images) {
            totalLength += 8 + image.length;
        }

        try (DataOutputStream stream = new DataOutputStream(Files.newOutputStream(output))) {
            stream.writeBytes("icns");
            stream.writeInt(totalLength);
            for (int index = 0; index < images.size(); index++) {
                byte[] image = images.get(index);
                stream.writeBytes(ICNS_TYPES[index]);
                stream.writeInt(8 + image.length);
                stream.write(image);
            }
        }
    }

    private static List<byte[]> createPngImages(int[] sizes) throws IOException {
        List<byte[]> images = new ArrayList<>();
        for (int size : sizes) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(createIcon(size), "png", bytes);
            images.add(bytes.toByteArray());
        }
        return images;
    }

    private static void writeLittleEndianShort(DataOutputStream stream, int value) throws IOException {
        stream.writeByte(value & 0xff);
        stream.writeByte((value >>> 8) & 0xff);
    }

    private static void writeLittleEndianInt(DataOutputStream stream, int value) throws IOException {
        stream.writeByte(value & 0xff);
        stream.writeByte((value >>> 8) & 0xff);
        stream.writeByte((value >>> 16) & 0xff);
        stream.writeByte((value >>> 24) & 0xff);
    }
}
