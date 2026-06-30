import java.net.Socket;
import java.io.OutputStream;

public class PrinterTest {

    public static void main(String[] args) {

        String printerIp = "192.168.2.200";
        int printerPort = 9100;

        System.out.println("Trying to connect to printer...");

        try (
                Socket socket = new Socket(printerIp, printerPort);
                OutputStream os = socket.getOutputStream()
        ) {

            System.out.println("✅ Connected to printer!");

            // ESC/POS 初始化
            os.write(new byte[]{0x1B, 0x40});

            String content =
                    "\n\n" +
                            "========================\n" +
                            "   CONNECTION SUCCESS   \n" +
                            "========================\n" +
                            "Printer is reachable!\n" +
                            "\n\n";

            os.write(content.getBytes("GBK"));

            // 切纸
            os.write(new byte[]{0x1D, 0x56, 0x41, 0x10});

            os.flush();

            System.out.println("🧾 Test print sent!");

        } catch (Exception e) {

            System.out.println("❌ Connection failed");
            e.printStackTrace();
        }
    }
}