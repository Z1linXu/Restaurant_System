package com.restaurant.system.printing.transport;

import com.restaurant.system.printing.renderer.PrintMarkup;
import java.io.IOException;
import java.io.OutputStream;

/** Additional top feed, after ESC @ and before any receipt text. */
public final class EscPosPaperFeed {
    private EscPosPaperFeed() {}

    public static String writeTopFeed(OutputStream output, String content) throws IOException {
        if (content.startsWith(PrintMarkup.TOP_FEED_16MM)) {
            // GS P: vertical unit 25.4/254 = 0.1mm; ESC J 160: 16mm forward feed.
            // Restore default motion units so body spacing and the existing end cut stay unchanged.
            output.write(new byte[] {0x1D, 0x50, 0, (byte) 254, 0x1B, 0x4A, (byte) 160,
                0x1D, 0x50, 0, 0});
            return PrintMarkup.stripTopFeed(content);
        }
        return content;
    }
}
