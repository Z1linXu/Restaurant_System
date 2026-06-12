package com.restaurant.system.printing.transport;

public enum EscPosFontSizeMode {
    XS("XS", "Extra Small", new byte[] {0x1D, 0x21, 0x00}, new byte[] {0x1D, 0x21, 0x00}),
    SMALL("SMALL", "Small", new byte[] {0x1D, 0x21, 0x11}, new byte[] {0x1D, 0x21, 0x00}),
    MEDIUM("MEDIUM", "Medium", new byte[] {0x1D, 0x21, 0x22}, new byte[] {0x1D, 0x21, 0x00}),
    LARGE("LARGE", "Large", new byte[] {0x1D, 0x21, 0x33}, new byte[] {0x1D, 0x21, 0x00}),
    XL("XL", "Extra Large", new byte[] {0x1D, 0x21, 0x44}, new byte[] {0x1D, 0x21, 0x00});

    public static final String DEFAULT_CODE = MEDIUM.code;

    public final String code;
    public final String label;
    public final byte[] activate_bytes;
    public final byte[] reset_bytes;

    EscPosFontSizeMode(String code, String label, byte[] activateBytes, byte[] resetBytes) {
        this.code = code;
        this.label = label;
        this.activate_bytes = activateBytes;
        this.reset_bytes = resetBytes;
    }

    public static EscPosFontSizeMode fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return MEDIUM;
        }
        for (EscPosFontSizeMode mode : values()) {
            if (mode.code.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return MEDIUM;
    }
}
