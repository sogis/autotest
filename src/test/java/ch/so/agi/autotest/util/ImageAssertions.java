package ch.so.agi.autotest.util;

/**
 * Entry point for AssertJ assertions over encoded image responses.
 */
public final class ImageAssertions {

    private ImageAssertions() {
    }

    public static ImageAssert assertThatImage(byte[] encodedImage) {
        return new ImageAssert(encodedImage);
    }
}
