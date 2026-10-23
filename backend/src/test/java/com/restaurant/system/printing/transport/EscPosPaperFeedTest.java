package com.restaurant.system.printing.transport;

import static org.junit.jupiter.api.Assertions.*;
import com.restaurant.system.printing.renderer.PrintMarkup;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

class EscPosPaperFeedTest {
    @Test
    void feedUsesMillimetreMotionUnitsThenRestoresDefaultsWithoutRemovingExistingBlankLines() throws Exception {
        String original = "\n\n" + PrintMarkup.large("1里") + "\nitem\n";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertEquals(original, EscPosPaperFeed.writeTopFeed(output, PrintMarkup.withTopFeed(original)));
        assertArrayEquals(new byte[] {0x1D, 0x50, 0, (byte)254, 0x1B, 0x4A, (byte)160,
            0x1D, 0x50, 0, 0}, output.toByteArray());
        assertEquals(16.0, 160 * 25.4 / 254, 0.00001);
        // Mechanical pitch truncation still leaves more than 15mm on typical ESC/POS resolutions.
        for (int dpi : new int[] {144, 180, 203, 300, 406}) {
            assertTrue(Math.floor(16.0 * dpi / 25.4) * 25.4 / dpi >= 15.0);
        }
    }

    @Test
    void repeatedLayoutApplicationAndDuplicatePrefixFeedOnlyOncePerTicket() throws Exception {
        String text = PrintMarkup.large("1-右") + " (UPDATE)\nitem\n";
        assertEquals(PrintMarkup.withTopFeed(text), PrintMarkup.withTopFeed(PrintMarkup.withTopFeed(text)));
        for (int copy = 0; copy < 2; copy++) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            assertEquals(text, EscPosPaperFeed.writeTopFeed(output,
                PrintMarkup.TOP_FEED_16MM + PrintMarkup.TOP_FEED_16MM + text));
            assertEquals(11, output.size());
        }
    }

    @Test
    void unmarkedTakeoutAndLiteralMarkerInsideBodyDoNotFeed() throws Exception {
        String text = "TAKEOUT\nitem " + PrintMarkup.TOP_FEED_16MM;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertEquals(text, EscPosPaperFeed.writeTopFeed(output, text));
        assertEquals(0, output.size());
    }
}
