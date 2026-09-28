package com.restaurant.pad;

import android.util.Base64;
import android.webkit.JavascriptInterface;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public class PrinterPluginBridge {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final DeadlineTcpPrinter transport;
    private volatile boolean closed;
    public PrinterPluginBridge() { this(() -> true); }
    public PrinterPluginBridge(java.util.function.BooleanSupplier owner) {
        transport = new DeadlineTcpPrinter(() -> !closed && owner.getAsBoolean());
    }
    public void close() { closed = true; transport.close(); executor.shutdownNow(); }
    public static boolean nativeBusy() { return DeadlineTcpPrinter.busy(); }
    public JSONObject nativeStatus() throws Exception {
        DeadlineTcpPrinter.Result result = transport.lastResult();
        JSONObject status = new JSONObject();
        status.put("phase", result.phase); status.put("error", result.error);
        status.put("bytes_written", result.bytesWritten); status.put("elapsed_ms", result.elapsedMs);
        status.put("uncertain", result.uncertain); status.put("operation_stopped", result.stopped);
        status.put("operation_active", nativeBusy()); return status;
    }

    @JavascriptInterface
    public String testConnection(String jsonRequest) {
        return runPrinterTask(() -> {
            JSONObject request = new JSONObject(jsonRequest);
            String ip = request.getString("ip");
            int port = request.optInt("port", 9100);
            int timeoutMs = request.optInt("timeoutMs", 3000);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, port), timeoutMs);
                socket.setSoTimeout(timeoutMs);
            }
            return success("Connection successful");
        });
    }

    @JavascriptInterface
    public String printRawTcp(String jsonRequest) {
        try {
            JSONObject request = new JSONObject(jsonRequest);
            String ip = request.getString("ip");
            int port = request.optInt("port", 9100);
            int timeoutMs = request.optInt("timeoutMs", 3000);
            byte[] payload = Base64.decode(request.getString("payloadBase64"), Base64.DEFAULT);
            String endpoint = ip + ":" + port;
            timeoutMs = Math.max(500, Math.min(timeoutMs, 10000));
            DeadlineTcpPrinter.Result result = transport.send(ip, port, timeoutMs, Math.min(30000L, Math.max(10000L, timeoutMs * 3L)), payload);
            JSONObject response = new JSONObject(result.success ? success("Print payload sent", endpoint, result.bytesWritten)
                : failure(result.uncertain ? "ANDROID_PRINT_UNCERTAIN" : result.error,
                    result.uncertain ? "打印结果不确定，请人工检查；禁止自动重发" : "Native print execution failed",
                    result.phase, result.bytesWritten, null, endpoint));
            response.put("uncertain", result.uncertain); response.put("operation_stopped", result.stopped);
            response.put("elapsed_ms", result.elapsedMs);
            return response.toString();
        } catch (Exception ex) { return failure("NATIVE_REQUEST_INVALID", "Invalid native print request"); }
    }

    private String runPrinterTask(Callable<String> task) {
        try {
            return executor.submit(task).get();
        } catch (Exception exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            return failure(resolveErrorCode(cause), cause.getMessage() == null ? cause.toString() : cause.getMessage());
        }
    }

    private String success(String message) throws Exception {
        return success(message, null, 0);
    }

    private String success(String message, String endpoint, int bytesWritten) throws Exception {
        JSONObject response = new JSONObject();
        response.put("success", true);
        response.put("message", message);
        response.put("phase", "DONE");
        response.put("bytes_written", Math.max(bytesWritten, 0));
        if (endpoint != null && !endpoint.isBlank()) {
            response.put("endpoint", endpoint);
        }
        return response.toString();
    }

    private String failure(String code, String message) {
        return failure(code, message, "UNKNOWN", 0, null, null);
    }

    private String failure(String code, String message, String phase, int bytesWritten, Throwable throwable, String endpoint) {
        try {
            JSONObject response = new JSONObject();
            response.put("success", false);
            response.put("error_code", code);
            response.put("native_error_code", code);
            response.put("phase", phase == null || phase.isBlank() ? "UNKNOWN" : phase);
            response.put("bytes_written", Math.max(bytesWritten, 0));
            if (throwable != null) {
                response.put("exception_class", throwable.getClass().getSimpleName());
                response.put("exception_message", throwable.getMessage() == null ? throwable.toString() : throwable.getMessage());
            }
            if (endpoint != null && !endpoint.isBlank()) {
                response.put("endpoint", endpoint);
            }
            response.put("message", message);
            return response.toString();
        } catch (Exception ignored) {
            return "{\"success\":false,\"error_code\":\"UNKNOWN\",\"message\":\"Unknown printer error\"}";
        }
    }

    private String resolveErrorCode(Throwable throwable) {
        return resolveErrorCode(throwable, "UNKNOWN");
    }

    private String resolveErrorCode(Throwable throwable, String phase) {
        if ("FLUSH".equals(phase) && throwable instanceof java.io.IOException) {
            return "FLUSH_FAILED";
        }
        if ("WRITE".equals(phase) && throwable instanceof java.io.IOException) {
            return "WRITE_FAILED";
        }
        if (throwable instanceof SocketTimeoutException) {
            return "TIMEOUT";
        }
        if (throwable instanceof ConnectException) {
            return "CONNECTION_REFUSED";
        }
        if (throwable instanceof NoRouteToHostException || throwable instanceof UnknownHostException) {
            return "UNREACHABLE";
        }
        if (throwable instanceof java.io.IOException) {
            return "WRITE_FAILED";
        }
        return "UNKNOWN";
    }
}
