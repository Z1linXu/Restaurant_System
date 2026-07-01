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
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, port), timeoutMs);
                socket.setSoTimeout(timeoutMs);
                OutputStream outputStream = socket.getOutputStream();
                outputStream.write(payload);
                outputStream.flush();
            }
            return success("Print payload sent");
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
        JSONObject response = new JSONObject();
        response.put("success", true);
        response.put("message", message);
        return response.toString();
    }

    private String failure(String code, String message) {
        try {
            JSONObject response = new JSONObject();
            response.put("success", false);
            response.put("error_code", code);
            response.put("message", message);
            return response.toString();
        } catch (Exception ignored) {
            return "{\"success\":false,\"error_code\":\"UNKNOWN\",\"message\":\"Unknown printer error\"}";
        }
    }

    private String resolveErrorCode(Throwable throwable) {
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
