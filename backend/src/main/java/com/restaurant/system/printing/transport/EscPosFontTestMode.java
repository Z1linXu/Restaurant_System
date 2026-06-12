package com.restaurant.system.printing.transport;

public enum EscPosFontTestMode {
    FONT_TEST_A("FONT TEST A", new byte[] {0x1B, 0x21, 0x10}, new byte[] {0x1B, 0x21, 0x00}),
    FONT_TEST_B("FONT TEST B", new byte[] {0x1B, 0x21, 0x30}, new byte[] {0x1B, 0x21, 0x00}),
    FONT_TEST_C("FONT TEST C", new byte[] {0x1D, 0x21, 0x11}, new byte[] {0x1D, 0x21, 0x00}),
    FONT_TEST_D_3X("FONT TEST D (3X)", new byte[] {0x1D, 0x21, 0x22}, new byte[] {0x1D, 0x21, 0x00}),
    FONT_TEST_E_4X("FONT TEST E (4X)", new byte[] {0x1D, 0x21, 0x33}, new byte[] {0x1D, 0x21, 0x00});

    public final String label;
    public final byte[] activate_bytes;
    public final byte[] reset_bytes;

    EscPosFontTestMode(String label, byte[] activateBytes, byte[] resetBytes) {
        this.label = label;
        this.activate_bytes = activateBytes;
        this.reset_bytes = resetBytes;
    }

    public String activateHex() {
        return toHex(activate_bytes);
    }

    public String resetHex() {
        return toHex(reset_bytes);
    }

    private String toHex(byte[] value) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length; index++) {
            if (index > 0) {
                builder.append(' ');
            }
            builder.append(String.format("%02X", value[index] & 0xFF));
        }
        return builder.toString();
    }
}
