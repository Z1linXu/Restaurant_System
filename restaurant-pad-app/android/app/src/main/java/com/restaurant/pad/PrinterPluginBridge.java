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
        return runPrinterTask(() -> {
            JSONObject request = new JSONObject(jsonRequest);
            String ip = request.getString("ip");
            int port = request.optInt("port", 9100);
            int timeoutMs = request.optInt("timeoutMs", 3000);
            byte[] payload = Base64.decode(request.getString("payloadBase64"), Base64.DEFAULT);
            String endpoint = ip + ":" + port;
            String phase = "CONNECT";
            int bytesWritten = 0;
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(ip, port), timeoutMs);
                socket.setSoTimeout(timeoutMs);
                OutputStream outputStream = socket.getOutputStream();
                phase = "WRITE";
                int offset = 0;
                while (offset < payload.length) {
                    int length = Math.min(4096, payload.length - offset);
                    outputStream.write(payload, offset, length);
                    offset += length;
                    bytesWritten = offset;
                }
                phase = "FLUSH";
                outputStream.flush();
                phase = "CLOSE";
                socket.close();
                socket = null;
            } catch (Exception exception) {
                return failure(
                    resolveErrorCode(exception, phase),
                    exception.getMessage() == null ? exception.toString() : exception.getMessage(),
                    phase,
                    bytesWritten,
                    exception,
                    endpoint
                );
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (Exception ignored) {
                        // Close failures after an already-failed socket operation do not change the primary diagnostic.
                    }
                }
            }
            return success("Print payload sent", endpoint, bytesWritten);
        });
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
