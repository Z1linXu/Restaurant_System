package com.restaurant.pad;

import java.net.URI;

/** Fixed merchant builds accept only their HTTPS cloud and the bundled asset origin. */
final class MerchantEndpointPolicy {
    static boolean allows(String url, String fixedApiBase) {
        if (fixedApiBase.isEmpty()) return true; // Existing configurable Lanzhou build.
        try {
            URI uri = new URI(url);
            URI api = new URI(fixedApiBase);
            return "https".equals(uri.getScheme()) && uri.getUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443)
                && ("restaurant-pad.local".equals(uri.getHost()) || api.getHost().equals(uri.getHost()));
        } catch (Exception ignored) { return false; }
    }
}
