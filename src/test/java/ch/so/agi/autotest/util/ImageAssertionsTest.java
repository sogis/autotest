package ch.so.agi.autotest.util;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static ch.so.agi.autotest.util.ImageAssertions.assertThatImage;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageAssertionsTest {

    private static final Color RED = new Color(255, 0, 0, 255);
    private static final Color GREEN = new Color(0, 255, 0, 128);
    private static final Color BLUE = new Color(0, 0, 255, 255);

    @Test
    void recognizesEncodedImageFormat() {
        byte[] image = pngOf(
            new Color[][] {{RED, RED}, {RED, RED}});

        assertThatImage(image).isOfType("PNG");

        assertThatThrownBy(() -> assertThatImage(image).isOfType("jpeg"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("<jpeg>")
            .hasMessageContaining("<png>");
    }

    @Test
    void assertsExactRgbaAtPixel() {
        byte[] image = pngOf(
            new Color[][] {{RED, GREEN}, {BLUE, RED}});

        assertThatImage(image)
            .hasPixelColor(0, 0, RED)
            .hasPixelColor(1, 0, GREEN)
            .hasPixelColor(0, 1, BLUE);

        assertThatThrownBy(() -> assertThatImage(image).hasPixelColor(1, 0, RED))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("pixel (1, 0)")
            .hasMessageContaining("rgba(255, 0, 0, 255)")
            .hasMessageContaining("rgba(0, 255, 0, 128)");
    }

    @Test
    void assertsUniformRgbaAcrossSquare() {
        byte[] image = pngOf(new Color[][] {
            {RED, RED, BLUE},
            {RED, RED, BLUE},
            {BLUE, BLUE, BLUE}
        });

        assertThatImage(image).hasSquareColor(0, 0, 2, RED);

        assertThatThrownBy(() -> assertThatImage(image).hasSquareColor(1, 0, 2, RED))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("pixel (2, 0)")
            .hasMessageContaining("rgba(0, 0, 255, 255)");
    }

    @Test
    void rejectsInvalidImageAndInvalidRegionsClearly() {
        assertThatThrownBy(() -> assertThatImage(new byte[] {1, 2, 3}))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("no matching reader");

        byte[] image = pngOf(new Color[][] {{RED, RED}, {RED, RED}});

        assertThatThrownBy(() -> assertThatImage(image).hasPixelColor(2, 0, RED))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("inside 2x2 image");
        assertThatThrownBy(() -> assertThatImage(image).hasSquareColor(1, 1, 2, RED))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("to fit inside 2x2 image");
        assertThatThrownBy(() -> assertThatImage(image).hasSquareColor(0, 0, 0, RED))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("size to be positive");
    }

    private static byte[] pngOf(Color[][] colors) {
        BufferedImage image = new BufferedImage(colors[0].length, colors.length,
            BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < colors.length; y++) {
            for (int x = 0; x < colors[y].length; x++) {
                image.setRGB(x, y, colors[y][x].getRGB());
            }
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException("PNG writer is unavailable");
            }
            return output.toByteArray();
        }
        catch (IOException e) {
            throw new IllegalStateException("Cannot encode synthetic PNG fixture", e);
        }
    }
}
