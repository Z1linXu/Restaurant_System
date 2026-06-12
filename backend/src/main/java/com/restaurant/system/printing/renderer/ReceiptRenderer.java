package com.restaurant.system.printing.renderer;

import com.restaurant.system.printing.dto.PrintRenderRequest;

public interface ReceiptRenderer {

    String getModuleCode();

    String render(PrintRenderRequest request);
}
