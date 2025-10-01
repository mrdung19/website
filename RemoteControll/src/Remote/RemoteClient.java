package Remote;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import javax.imageio.ImageIO;

public class RemoteClient extends JFrame {
    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;
    private JLabel screenLabel;
    private BufferedImage lastImage;
    private int serverWidth = 1920;
    private int serverHeight = 1080;

    public RemoteClient(String host, int port) throws Exception {
        socket = new Socket(host, port);
        dis = new DataInputStream(socket.getInputStream());
        dos = new DataOutputStream(socket.getOutputStream());

        setTitle("Remote Client - " + host);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        screenLabel = new JLabel();
        screenLabel.setHorizontalAlignment(JLabel.CENTER);
        screenLabel.setVerticalAlignment(JLabel.CENTER);

        JButton screenshotBtn = new JButton("📸 Chụp màn hình");
        screenshotBtn.addActionListener(e -> saveScreenshot());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(screenshotBtn);

        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(screenLabel), BorderLayout.CENTER);

        new Thread(this::receiveLoop).start();

        // Mouse move
        screenLabel.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
                sendMouseMove(e);
            }
        });

        // Mouse click
        screenLabel.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                sendCommand("CLICK");
            }
        });

        // Mouse scroll
        screenLabel.addMouseWheelListener(e -> {
            sendCommand("SCROLL " + e.getWheelRotation());
        });

        // Keyboard
        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                sendCommand("KEY_PRESS " + e.getKeyCode());
            }
            public void keyReleased(KeyEvent e) {
                sendCommand("KEY_RELEASE " + e.getKeyCode());
            }
        });

        setFocusable(true);
        requestFocusInWindow();
        setVisible(true);
    }

    private void sendMouseMove(MouseEvent e) {
        try {
            if (lastImage != null) {
                double scaleX = (double) serverWidth / screenLabel.getWidth();
                double scaleY = (double) serverHeight / screenLabel.getHeight();
                int realX = (int) (e.getX() * scaleX);
                int realY = (int) (e.getY() * scaleY);
                dos.writeUTF("MOVE " + realX + " " + realY);
                dos.flush();
            }
        } catch (Exception ignored) {}
    }

    private void sendCommand(String cmd) {
        try {
            dos.writeUTF(cmd);
            dos.flush();
        } catch (Exception ignored) {}
    }

    private void receiveLoop() {
        try {
            while (true) {
                String type = dis.readUTF();
                if ("IMAGE".equals(type)) {
                    int len = dis.readInt();
                    byte[] data = new byte[len];
                    dis.readFully(data);

                    lastImage = ImageIO.read(new ByteArrayInputStream(data));
                    if (lastImage != null) {
                        Image scaled = lastImage.getScaledInstance(
                                screenLabel.getWidth(),
                                screenLabel.getHeight(),
                                Image.SCALE_SMOOTH
                        );
                        screenLabel.setIcon(new ImageIcon(scaled));
                    }
                } else if (type.startsWith("RESOLUTION")) {
                    String[] parts = type.split(" ");
                    serverWidth = Integer.parseInt(parts[1]);
                    serverHeight = Integer.parseInt(parts[2]);
                    System.out.println("📏 Resolution server: " + serverWidth + "x" + serverHeight);
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "❌ Mất kết nối tới server!");
            System.exit(0);
        }
    }

    private void saveScreenshot() {
        if (lastImage != null) {
            try {
                String filename = "screenshot_" + System.currentTimeMillis() + ".png";
                ImageIO.write(lastImage, "png", new File(filename));
                JOptionPane.showMessageDialog(this, "Ảnh đã lưu: " + filename);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "❌ Lỗi khi lưu ảnh: " + e.getMessage());
            }
        } else {
            JOptionPane.showMessageDialog(this, "⚠️ Chưa có ảnh để chụp!");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JTextField ipField = new JTextField("127.0.0.1");
            JTextField portField = new JTextField("6001");

            JPanel panel = new JPanel(new GridLayout(2, 2));
            panel.add(new JLabel("Server IP:"));
            panel.add(ipField);
            panel.add(new JLabel("Port:"));
            panel.add(portField);

            int result = JOptionPane.showConfirmDialog(null, panel, "Nhập thông tin kết nối", JOptionPane.OK_CANCEL_OPTION);

            if (result == JOptionPane.OK_OPTION) {
                String host = ipField.getText().trim();
                int port = Integer.parseInt(portField.getText().trim());
                try {
                    new RemoteClient(host, port);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null, "Không thể kết nối: " + e.getMessage());
                }
            }
        });
    }
}
