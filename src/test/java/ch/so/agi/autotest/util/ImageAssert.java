package ch.so.agi.autotest.util;

import org.assertj.core.api.AbstractAssert;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

/**
 * AssertJ assertions for an encoded image, including its file format and exact RGBA pixels.
 */
public final class ImageAssert extends AbstractAssert<ImageAssert, byte[]> {

    private final BufferedImage image;
    private final String format;

    ImageAssert(byte[] encodedImage) {
        super(encodedImage, ImageAssert.class);
        DecodedImage decoded = decode(encodedImage);
        image = decoded.image();
        format = decoded.format();
    }

    public ImageAssert isOfType(String expectedFormat) {
        if (expectedFormat == null || expectedFormat.isBlank()) {
            failWithMessage("Expected image format must not be blank");
        }
        if (!format.equalsIgnoreCase(expectedFormat)) {
            failWithMessage("Expected image format to be <%s> but was <%s>", expectedFormat, format);
        }
        return this;
    }

    public ImageAssert hasPixelColor(int x, int y, Color expected) {
        validatePixelCoordinates(x, y);
        validateExpectedColor(expected);
        assertColorAt(x, y, expected);
        return this;
    }

    public ImageAssert hasSquareColor(int x, int y, int size, Color expected) {
        validateSquare(x, y, size);
        validateExpectedColor(expected);
        for (int pixelY = y; pixelY < y + size; pixelY++) {
            for (int pixelX = x; pixelX < x + size; pixelX++) {
                assertColorAt(pixelX, pixelY, expected);
            }
        }
        return this;
    }

    private void validatePixelCoordinates(int x, int y) {
        if (x < 0 || x >= image.getWidth() || y < 0 || y >= image.getHeight()) {
            failWithMessage("Expected pixel coordinates (%d, %d) to be inside %dx%d image",
                x, y, image.getWidth(), image.getHeight());
        }
    }

    private void validateSquare(int x, int y, int size) {
        if (size <= 0) {
            failWithMessage("Expected square size to be positive but was <%d>", size);
        }
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()
            || size > image.getWidth() - x || size > image.getHeight() - y) {
            failWithMessage("Expected %dx%d square at (%d, %d) to fit inside %dx%d image",
                size, size, x, y, image.getWidth(), image.getHeight());
        }
    }

    private void validateExpectedColor(Color expected) {
        if (expected == null) {
            failWithMessage("Expected image color must not be null");
        }
    }

    private void assertColorAt(int x, int y, Color expected) {
        Color actualColor = new Color(image.getRGB(x, y), true);
        if (actualColor.getRGB() != expected.getRGB()) {
            failWithMessage("Expected pixel (%d, %d) to have RGBA <%s> but was <%s>",
                x, y, rgba(expected), rgba(actualColor));
        }
    }

    private static String rgba(Color color) {
        return "rgba(%d, %d, %d, %d)".formatted(
            color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    private static DecodedImage decode(byte[] encodedImage) {
        if (encodedImage == null) {
            throw new AssertionError("Expected encoded image bytes but received null");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(encodedImage))) {
            if (input == null) {
                throw new AssertionError("Expected encoded image bytes but ImageIO could not open them");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new AssertionError("Expected encoded image bytes but ImageIO found no matching reader");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                BufferedImage decodedImage = reader.read(0);
                if (decodedImage == null) {
                    throw new AssertionError("Expected encoded image bytes but ImageIO could not decode an image");
                }
                return new DecodedImage(decodedImage,
                    reader.getFormatName().toLowerCase(Locale.ROOT));
            }
            finally {
                reader.dispose();
            }
        }
        catch (IOException e) {
            throw new AssertionError("Expected valid encoded image bytes but ImageIO could not decode them", e);
        }
    }

    private record DecodedImage(BufferedImage image, String format) {
    }
}
