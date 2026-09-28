package com.restaurant.system.printing.security;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Verified execution identity, never an order-business snapshot. */
public final class PrintOriginContext {
    public static final String ATTRIBUTE = PrintOriginContext.class.getName();
    private static final ThreadLocal<Origin> DISPATCH = new ThreadLocal<>();
    public record Origin(Long deviceId, Long storeId, Long organizationId) {}
    private PrintOriginContext() {}

    public static Long deviceFor(Long storeId, Long organizationId) {
        Origin origin = DISPATCH.get();
        if (origin == null && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request) {
            origin = (Origin) request.getRequest().getAttribute(ATTRIBUTE);
        }
        if (origin == null) return null;
        if (!java.util.Objects.equals(storeId, origin.storeId())
            || !java.util.Objects.equals(organizationId, origin.organizationId())) {
            throw new com.restaurant.system.common.auth.ForbiddenException("Print origin scope mismatch");
        }
        return origin.deviceId();
    }

    public interface Scope extends AutoCloseable { @Override void close(); }
    public static Scope dispatch(Long deviceId, Long storeId, Long organizationId) {
        Origin previous = DISPATCH.get();
        DISPATCH.set(new Origin(deviceId, storeId, organizationId));
        return () -> { if (previous == null) DISPATCH.remove(); else DISPATCH.set(previous); };
    }
}
