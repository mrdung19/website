package Remote;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import javax.imageio.ImageIO;

public class RemoteServer {
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private DataInputStream dis;
    private DataOutputStream dos;
    private Robot robot;

    private boolean running = true;
    private int screenWidth;
    private int screenHeight;

    public RemoteServer(int port) {
        while (true) {
            try {
                serverSocket = new ServerSocket(port);
                System.out.println("✅ Server đang chạy trên cổng: " + port);
                break;
            } catch (BindException e) {
                System.out.println("⚠️ Port " + port + " đã bận, thử port " + (port + 1));
                port++;
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
    }

    public void start() {
        try {
            System.out.println("⏳ Đang chờ client kết nối...");
            clientSocket = serverSocket.accept();
            System.out.println("✅ Client đã kết nối: " + clientSocket.getInetAddress());

            dis = new DataInputStream(clientSocket.getInputStream());
            dos = new DataOutputStream(clientSocket.getOutputStream());
            robot = new Robot();

            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            screenWidth = screenSize.width;
            screenHeight = screenSize.height;

            // gửi độ phân giải màn hình cho client
            dos.writeUTF("RESOLUTION " + screenWidth + " " + screenHeight);
            dos.flush();

            new Thread(this::listenCommands).start();
            new Thread(this::streamScreen).start();

        } catch (Exception e) {
            System.out.println("❌ Lỗi khi khởi động server:");
            e.printStackTrace();
            stopServer();
        }
    }

    private void listenCommands() {
        try {
            while (running) {
                String command = dis.readUTF();
                System.out.println("📩 Lệnh nhận: " + command);

                if (command.startsWith("MOVE")) {
                    String[] parts = command.split(" ");
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    robot.mouseMove(x, y);

                } else if (command.equals("CLICK")) {
                    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

                } else if (command.startsWith("SCROLL")) {
                    int notches = Integer.parseInt(command.split(" ")[1]);
                    robot.mouseWheel(notches);

                } else if (command.startsWith("KEY_PRESS")) {
                    int keyCode = Integer.parseInt(command.split(" ")[1]);
                    robot.keyPress(keyCode);

                } else if (command.startsWith("KEY_RELEASE")) {
                    int keyCode = Integer.parseInt(command.split(" ")[1]);
                    robot.keyRelease(keyCode);

                } else {
                    System.out.println("⚠️ Unknown command: " + command);
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Client ngắt kết nối hoặc lỗi: " + e.getMessage());
            stopServer();
        }
    }

    private void streamScreen() {
        try {
            while (running) {
                Rectangle screenRect = new Rectangle(screenWidth, screenHeight);
                BufferedImage screen = robot.createScreenCapture(screenRect);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(screen, "jpg", baos);
                byte[] imageBytes = baos.toByteArray();

                dos.writeUTF("IMAGE");
                dos.writeInt(imageBytes.length);
                dos.write(imageBytes);
                dos.flush();

                Thread.sleep(100); // ~10 FPS
            }
        } catch (Exception e) {
            System.out.println("❌ Dừng stream: " + e.getMessage());
            stopServer();
        }
    }

    private void stopServer() {
        running = false;
        try { if (clientSocket != null) clientSocket.close(); } catch (IOException ignored) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        System.out.println("🛑 Server đã dừng.");
    }

    public static void main(String[] args) {
        int defaultPort = 6001;
        System.out.println("🔄 Khởi động Remote Server...");
        RemoteServer server = new RemoteServer(defaultPort);
        if (server.serverSocket != null) {
            server.start();
        }
    }
}
