package com.restaurant.system.common.auth;
import java.net.Socket;
import java.io.OutputStream;

public class PrinterTest {

    public static void main(String[] args) {
        String printerIp = "192.168.1.100"; // 👉 改成你的打印机IP
        int[] ports = {9100, 515, 631};

        boolean connected = false;

        for (int port : ports) {
            try (Socket socket = new Socket(printerIp, port)) {
                System.out.println("✅ Connected on port: " + port);

                // 打印测试小票
                printSuccess(socket);

                connected = true;
                break;

            } catch (Exception e) {
                System.out.println("❌ Failed on port: " + port);
            }
        }

        if (!connected) {
            System.out.println("❌ Could not connect to printer on any port");
        }
    }

    private static void printSuccess(Socket socket) {
        try {
            OutputStream os = socket.getOutputStream();

            String content =
                    "\n\n" +
                            "************************\n" +
                            "   CONNECTION SUCCESS   \n" +
                            "************************\n" +
                            "Printer is reachable!\n" +
                            "\n\n";

            os.write(content.getBytes("GBK")); // 中文/英文都安全
            os.flush();

            System.out.println("🧾 Test print sent");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}