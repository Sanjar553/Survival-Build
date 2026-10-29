import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public class SurvivalGame {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Surv:ival");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);

            GamePanel panel = new GamePanel();
            frame.add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            panel.startGame();
        });
    }
}

class GamePanel extends JPanel implements ActionListener, KeyListener {
    // --- Konstantalar ---
    private static final int TILE_SIZE = 8;           // Har bir blok 8x8 piksel
    private static final int COLS = 100;              // Dunyo kengligi (bloklarda)
    private static final int ROWS = 60;               // Dunyo balandligi
    private static final int SCREEN_WIDTH = 800;
    private static final int SCREEN_HEIGHT = 600;
    private static final int FPS = 60;
    private static final double GRAVITY = 0.5;        // Gravitatisiya kuchi
    private static final double JUMP_POWER = -7.5;    // Sakrash kuchi
    private static final double MOVE_SPEED = 2.5;     // Yurish tezligi

    // --- O'yin holati ---
    private enum GameState { BUILDING, PLAYING }
    private GameState state = GameState.BUILDING;

    private Timer timer;
    private boolean running = false;

    // Dunyo bloklari (true = to'siq bor)
    private final boolean[][] blocks = new boolean[COLS][ROWS];

    // O'yinchi (spawn qilingan odam)
    private double playerX, playerY;
    private double playerVX, playerVY;
    private boolean onGround = false;
    private boolean playerSpawned = false;

    // Kursor (qurish rejimida)
    private int cursorCol, cursorRow;

    // Klaviatura holati
    private boolean leftPressed, rightPressed, upPressed, spacePressed, shiftPressed;
    private boolean keyJustPressed = false; // bir marta bosish uchun

    // Kamera
    private int camX, camY;

    // Vaqtinchalik
    private long lastUpdateTime = System.nanoTime();

    public GamePanel() {
        setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
        setBackground(new Color(135, 206, 235)); // osmon rangi
        setFocusable(true);
        addKeyListener(this);
        setDoubleBuffered(true);

        // Boshlang'ich dunyo: pastki qator to'siq bilan to'ldirilgan
        for (int c = 0; c < COLS; c++) {
            blocks[c][ROWS - 1] = true;
        }

        cursorCol = COLS / 2;
        cursorRow = ROWS / 2;
    }

    public void startGame() {
        if (running) return;
        running = true;
        timer = new Timer(1000 / FPS, this);
        timer.start();
        requestFocusInWindow();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        long now = System.nanoTime();
        double delta = (now - lastUpdateTime) / 1_000_000_000.0; // sekundlarda
        lastUpdateTime = now;

        update(delta);
        repaint();
    }

    // ==================== YANGILANISH (UPDATE) ====================
    private void update(double delta) {
        // Klaviatura bosishlarini bir marta qayta ishlash
        if (keyJustPressed) {
            handleKeyJustPressed();
            keyJustPressed = false;
        }

        if (state == GameState.PLAYING && playerSpawned) {
            updatePlayerPhysics(delta);
        }

        // Kamera o'yinchini kuzatib boradi (agar spawn qilingan bo'lsa)
        if (playerSpawned) {
            int targetCamX = (int) (playerX - SCREEN_WIDTH / 2.0);
            int targetCamY = (int) (playerY - SCREEN_HEIGHT / 2.0);
            camX += (targetCamX - camX) * 0.1;
            camY += (targetCamY - camY) * 0.1;
            // Kamera chegaralarini cheklash
            camX = Math.max(0, Math.min(camX, COLS * TILE_SIZE - SCREEN_WIDTH));
            camY = Math.max(0, Math.min(camY, ROWS * TILE_SIZE - SCREEN_HEIGHT));
        } else {
            // Qurish rejimida kamera markazda
            camX = (COLS * TILE_SIZE - SCREEN_WIDTH) / 2;
            camY = (ROWS * TILE_SIZE - SCREEN_HEIGHT) / 2;
        }
    }

    private void handleKeyJustPressed() {
        // Rejim almashtirish
        if (shiftPressed) {
            if (state == GameState.BUILDING) {
                spawnPlayer();
            } else {
                state = GameState.BUILDING;
                playerSpawned = false; // ixtiyoriy: o'yinchini o'chirish
            }
        }

        // Qurish rejimida blok qo'yish yoki o'chirish
        if (state == GameState.BUILDING) {
            // Kursor blokini aniqlash (ekran koordinatalaridan dunyo koordinatalariga)
            Point mouse = getMousePosition();
            if (mouse != null) {
                int worldX = mouse.x + camX;
                int worldY = mouse.y + camY;
                int col = worldX / TILE_SIZE;
                int row = worldY / TILE_SIZE;
                if (col >= 0 && col < COLS && row >= 0 && row < ROWS) {
                    cursorCol = col;
                    cursorRow = row;

                    if (SwingUtilities.isLeftMouseButton(mouseEvent)) {
                        blocks[col][row] = true; // blok qo'yish
                    } else if (SwingUtilities.isRightMouseButton(mouseEvent)) {
                        blocks[col][row] = false; // blokni o'chirish
                    }
                }
            }
        }

        // O'yinchi sakrashi
        if (state == GameState.PLAYING && playerSpawned && (spacePressed || upPressed) && onGround) {
            playerVY = JUMP_POWER;
            onGround = false;
        }
    }

    private MouseEvent mouseEvent; // oxirgi sichqoncha hodisasi (soddalashtirilgan)

    // Sichqoncha hodisalarini kuzatish uchun (to'liqroq ishlov berish)
    {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                mouseEvent = e;
                keyJustPressed = true;
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouseEvent = e;
            }
        });
    }

    private void spawnPlayer() {
        // O'yinchini dunyoning markazida, yer sathidan biroz yuqorida spawn qilish
        int spawnCol = COLS / 2;
        int spawnRow = ROWS - 5;
        // Bo'sh joy topish
        while (spawnRow < ROWS && blocks[spawnCol][spawnRow]) {
            spawnRow--;
        }
        playerX = spawnCol * TILE_SIZE;
        playerY = spawnRow * TILE_SIZE - TILE_SIZE;
        playerVX = 0;
        playerVY = 0;
        onGround = false;
        playerSpawned = true;
        state = GameState.PLAYING;
    }

    // ==================== FIZIKA ====================
    private void updatePlayerPhysics(double delta) {
        // Gorizontal harakat
        double move = 0;
        if (leftPressed) move -= MOVE_SPEED;
        if (rightPressed) move += MOVE_SPEED;
        playerVX = move;

        // Gravitatsiya
        playerVY += GRAVITY;

        // Harakatni qo'llash (oddiy, kadrga bog'liq)
        double newX = playerX + playerVX;
        double newY = playerY + playerVY;

        // Kolliziya tekshiruvi (AABB)
        onGround = false;

        // X o'qi bo'yicha kolliziya
        if (playerVX != 0) {
            int checkX = (int) (newX + (playerVX > 0 ? TILE_SIZE : 0));
            int checkCol = checkX / TILE_SIZE;
            int topRow = (int) (playerY / TILE_SIZE);
            int bottomRow = (int) ((playerY + TILE_SIZE - 1) / TILE_SIZE);

            boolean collidedX = false;
            for (int r = topRow; r <= bottomRow; r++) {
                if (checkCol >= 0 && checkCol < COLS && r >= 0 && r < ROWS && blocks[checkCol][r]) {
                    collidedX = true;
                    break;
                }
            }
            if (!collidedX) {
                playerX = newX;
            } else {
                playerVX = 0;
            }
        }

        // Y o'qi bo'yicha kolliziya
        if (playerVY != 0) {
            int checkY = (int) (newY + (playerVY > 0 ? TILE_SIZE : 0));
            int checkRow = checkY / TILE_SIZE;
            int leftCol = (int) (playerX / TILE_SIZE);
            int rightCol = (int) ((playerX + TILE_SIZE - 1) / TILE_SIZE);

            boolean collidedY = false;
            for (int c = leftCol; c <= rightCol; c++) {
                if (c >= 0 && c < COLS && checkRow >= 0 && checkRow < ROWS && blocks[c][checkRow]) {
                    collidedY = true;
                    break;
                }
            }
            if (!collidedY) {
                playerY = newY;
            } else {
                if (playerVY > 0) { // pastga tushayotganda
                    onGround = true;
                }
                playerVY = 0;
            }
        }

        // Dunyo chegaralaridan chiqmaslik
        playerX = Math.max(0, Math.min(playerX, COLS * TILE_SIZE - TILE_SIZE));
        if (playerY > ROWS * TILE_SIZE) {
            // Agar yerga tushib ketsa, qayta spawn qilish yoki o'yinni tugatish
            spawnPlayer();
        }
    }

    // ==================== CHIZISH (RENDER) ====================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        // Antialiasing (ixtiyoriy)
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Ko'rinadigan bloklarni chizish
        int startCol = Math.max(0, camX / TILE_SIZE);
        int endCol = Math.min(COLS - 1, (camX + SCREEN_WIDTH) / TILE_SIZE);
        int startRow = Math.max(0, camY / TILE_SIZE);
        int endRow = Math.min(ROWS - 1, (camY + SCREEN_HEIGHT) / TILE_SIZE);

        for (int c = startCol; c <= endCol; c++) {
            for (int r = startRow; r <= endRow; r++) {
                if (blocks[c][r]) {
                    int screenX = c * TILE_SIZE - camX;
                    int screenY = r * TILE_SIZE - camY;
                    g2d.setColor(new Color(139, 69, 19)); // jigarrang tuproq
                    g2d.fillRect(screenX, screenY, TILE_SIZE, TILE_SIZE);
                    // Chegara chizish (ixtiyoriy)
                    g2d.setColor(Color.BLACK);
                    g2d.drawRect(screenX, screenY, TILE_SIZE, TILE_SIZE);
                }
            }
        }

        // O'yinchini chizish
        if (playerSpawned) {
            int screenX = (int) playerX - camX;
            int screenY = (int) playerY - camY;
            g2d.setColor(Color.RED);
            g2d.fillRect(screenX, screenY, TILE_SIZE, TILE_SIZE);
            // Ko'zlar (ixtiyoriy)
            g2d.setColor(Color.WHITE);
            g2d.fillRect(screenX + 2, screenY + 2, 2, 2);
            g2d.fillRect(screenX + 5, screenY + 2, 2, 2);
        }

        // Qurish rejimida kursorni ko'rsatish
        if (state == GameState.BUILDING) {
            int screenX = cursorCol * TILE_SIZE - camX;
            int screenY = cursorRow * TILE_SIZE - camY;
            g2d.setColor(new Color(255, 255, 0, 128)); // yarim shaffof sariq
            g2d.fillRect(screenX, screenY, TILE_SIZE, TILE_SIZE);
        }

        // UI matni
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.drawString("Rejim: " + (state == GameState.BUILDING ? "QURISH" : "O'YIN"), 10, 20);
        if (state == GameState.BUILDING) {
            g2d.drawString("Chap klik: blok qo'yish | O'ng klik: o'chirish", 10, 40);
            g2d.drawString("Shift: odamni spawn qilish", 10, 60);
        } else {
            g2d.drawString("A/D: harakat | W/Bo'shliq: sakrash | Shift: qurish rejimi", 10, 40);
        }
    }

    // ==================== KLAVIATURA ====================
    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT) leftPressed = true;
        if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) rightPressed = true;
        if (code == KeyEvent.VK_W || code == KeyEvent.VK_UP) upPressed = true;
        if (code == KeyEvent.VK_SPACE) spacePressed = true;
        if (code == KeyEvent.VK_SHIFT) shiftPressed = true;
        if (code == KeyEvent.VK_SPACE || code == KeyEvent.VK_UP) keyJustPressed = true;
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT) leftPressed = false;
        if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) rightPressed = false;
        if (code == KeyEvent.VK_W || code == KeyEvent.VK_UP) upPressed = false;
        if (code == KeyEvent.VK_SPACE) spacePressed = false;
        if (code == KeyEvent.VK_SHIFT) shiftPressed = false;
    }

    @Override
    public void keyTyped(KeyEvent e) {}
}