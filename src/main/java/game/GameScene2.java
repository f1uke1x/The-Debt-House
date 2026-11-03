package game;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * บ้านเก่า - ฉากที่ 2 : ห้องนอนพ่อและแม่ (Point and Click)
 *
 * คอมไพล์:  javac GameScene2.java
 * รัน:      java GameScene2
 * (ต้องรันจากโฟลเดอร์เดียวกับโฟลเดอร์ images/ และ sounds/)
 */
public class GameScene2 extends JPanel {

    // ===================== ตั้งค่าที่ปรับได้ =====================
    static final String IMG_ROOM    = "images/room2.png";
    static final String IMG_DOLL    = "images/doll.png";
    static final String IMG_CALENDAR = "images/calendar.png";
    static final String IMG_INVOICE = "images/invoice.png";

    // เสียง: ใช้ไฟล์ .wav (Java รองรับ WAV/AIFF/AU โดยตรง) ถ้าไม่มีไฟล์จะข้ามไปเฉยๆ
    static final String SND_CRY   = "sounds/cry.wav";
    static final String SND_PAPER = "sounds/paper.wav";

    // ตำแหน่งจุดคลิก (สัดส่วนของภาพ 0.0 - 1.0) : x, y, กว้าง, สูง
    static final double[] HOT_CALENDAR = {0.602, 0.234, 0.080, 0.192};
    static final double[] HOT_DRAWER   = {0.668, 0.500, 0.068, 0.145};

    // จังหวะอีเวนต์ (มิลลิวินาที)
    static final int T_WAIT_BEFORE = 500;   // รอก่อนเริ่มเฟด
    static final int T_FADE_OUT    = 700;  // เฟดเป็นสีดำ
    static final int T_DOLL_HOLD   = 1600;  // ค้างภาพตุ๊กตา
    static final int T_BLACK_HOLD  = 500;   // ดำค้างก่อนกลับฉากเดิม
    static final int T_FADE_IN     = 700;  // เฟดสว่างขึ้น

    static final String MSG_START = "";
        // ============================================================

    enum State { START, PLAY, VIEW, EVENT }

    private BufferedImage room, doll, calendar, invoice;

    private State state = State.START;
    private boolean seenCalendar = false, seenInvoice = false, eventDone = false;
    private BufferedImage viewing = null;     // รูปที่กำลังเปิดดู
    private boolean viewingIsCalendar = false;

    private boolean hoverCal = false, hoverDrawer = false;
    private float viewerAlpha = 0f;           // 0 = ปิด, 1 = เปิดเต็ม
    private float blackAlpha = 0f;            // ฉากดำ (ใช้ตอนเฟด)
    private float startAlpha = 1f;            // หน้าเริ่มเกม
    private boolean startFading = false;
    private boolean showDoll = false;

    // อีเวนต์ตุ๊กตา
    private int phase = 0;
    private long phaseStart = 0;

    // ข้อความ
    private String message = "";
    private long messageEnd = 0;

    private Clip cryClip;
    private Timer mainTimer;          // ตัวจับเวลาหลัก (หยุดเมื่อจบฉาก)
    private Runnable onFinish;        // เรียกหลังเหตุการณ์จบและรอ 2 วินาที
    static final int T_NEXT_SCENE = 2000;

    // พื้นที่วาดภาพ (คำนวณใหม่ทุกครั้งที่วาด)
    private int dx, dy, dw, dh;

    public GameScene2() {
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(1100, 825));
        setFocusable(true);
        loadImages();

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { updateHover(e.getX(), e.getY()); }
            @Override public void mouseClicked(MouseEvent e) { handleClick(e.getX(), e.getY()); }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE && state == State.VIEW) closeViewer();
            }
        });

        // ตัวจับเวลาหลัก ~60 fps
        mainTimer = new Timer(16, ev -> { tick(); repaint(); });
        mainTimer.start();
    }

    // ---------------------------------------------------------------- โหลดไฟล์
    private void loadImages() {
        try {
            room     = readGameImage(IMG_ROOM);
            doll     = readGameImage(IMG_DOLL);
            calendar = readGameImage(IMG_CALENDAR);
            invoice  = readGameImage(IMG_INVOICE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null,
                    "โหลดรูปฉากที่ 2 ไม่สำเร็จ:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            throw new IllegalStateException("Cannot load scene 2 images", ex);
        }
    }

    /** รองรับทั้ง images/ และ assets/images/ ของโปรเจกต์ JavaFX */
    static BufferedImage readGameImage(String path) throws IOException {
        String name = path.substring(path.lastIndexOf('/') + 1);
        String[] classpath = {"/assets/images/" + name, "/images/" + name};
        for (String resource : classpath) {
            try (java.io.InputStream in = GameScene2.class.getResourceAsStream(resource)) {
                if (in != null) {
                    BufferedImage img = ImageIO.read(in);
                    if (img != null) return img;
                }
            }
        }
        String[] locations = {
            path, "assets/images/" + name, "resources/assets/images/" + name,
            "src/main/resources/assets/images/" + name,
            "src/resources/assets/images/" + name,
            "resources/images/" + name, "src/main/resources/images/" + name
        };
        for (String location : locations) {
            File file = new File(location);
            if (file.isFile()) {
                BufferedImage img = ImageIO.read(file);
                if (img != null) return img;
                throw new IOException("ไฟล์ภาพเสียหรือไม่รองรับ: " + file.getAbsolutePath());
            }
        }
        throw new IOException("ไม่พบ " + name + " (ตรวจสอบ assets/images/ หรือ images/)");
    }

    // ---------------------------------------------------------------- เสียง
    
private Clip playSound(String path) {
    try {
        String fileName = new File(path).getName();

        java.net.URL url = getClass().getResource(
            "/assets/sounds/" + fileName
        );

        AudioInputStream audio;

        if (url != null) {
            audio = AudioSystem.getAudioInputStream(url);
        } else {
            File file = new File(path);
            audio = AudioSystem.getAudioInputStream(file);
        }

        try (AudioInputStream in = audio) {
            Clip clip = AudioSystem.getClip();
            clip.open(in);
            clip.start();
            return clip;
        }

    } catch (Exception e) {
        System.out.println("โหลดเสียงไม่ได้: " + path);
        e.printStackTrace();
        return null;
    }
}


    private void stopCry() {
        if (cryClip != null) {
            cryClip.stop();
            cryClip.close();
            cryClip = null;
        }
    }

    // ---------------------------------------------------------------- พิกัด
    private Rectangle2D.Double hotRect(double[] h) {
        return new Rectangle2D.Double(dx + h[0] * dw, dy + h[1] * dh, h[2] * dw, h[3] * dh);
    }

    private void updateHover(int mx, int my) {
        if (state != State.PLAY) {
            hoverCal = hoverDrawer = false;
            setCursor(Cursor.getPredefinedCursor(state == State.VIEW ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            return;
        }
        hoverCal = hotRect(HOT_CALENDAR).contains(mx, my);
        hoverDrawer = hotRect(HOT_DRAWER).contains(mx, my);
        setCursor(Cursor.getPredefinedCursor((hoverCal || hoverDrawer) ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    // ---------------------------------------------------------------- คลิก
    private void handleClick(int mx, int my) {
        switch (state) {
            case START:
                if (!startFading) {
                    startFading = true;
                    showMessage(MSG_START, 3000, 1200);
                }
                break;
            case PLAY:
                if (hotRect(HOT_CALENDAR).contains(mx, my)) openViewer(calendar, true);
                else if (hotRect(HOT_DRAWER).contains(mx, my)) openViewer(invoice, false);
                break;
            case VIEW:
                closeViewer();
                break;
            default:
                break; // ระหว่างอีเวนต์คลิกไม่ได้
        }
    }

    private void openViewer(BufferedImage img, boolean isCalendar) {
        viewing = img;
        viewingIsCalendar = isCalendar;
        state = State.VIEW;
        viewerAlpha = 1f;
        hoverCal = hoverDrawer = false;
        playSound(SND_PAPER);
    }

    private void closeViewer() {
        if (viewingIsCalendar) seenCalendar = true; else seenInvoice = true;
        state = State.PLAY;
        viewerAlpha = 0f;
        // viewerAlpha จะค่อยๆ ลดลงเองใน tick()
        if (seenCalendar && seenInvoice && !eventDone) startEvent();
    }

    // ---------------------------------------------------------------- อีเวนต์
    private void startEvent() {
        eventDone = true;
        state = State.EVENT;
        phase = 0;
        phaseStart = System.currentTimeMillis();
    }

    private void showMessage(String text, int durationMs, int delayMs) {
        message = text;
        messageEnd = System.currentTimeMillis() + delayMs + durationMs;
        messageStart = System.currentTimeMillis() + delayMs;
    }
    private long messageStart = 0;

    // ---------------------------------------------------------------- อัปเดตทุกเฟรม
    private void tick() {
        long now = System.currentTimeMillis();

        // หน้าเริ่มเกมค่อยๆ จาง
        if (startFading) {
            startAlpha -= 0.016f;
            if (startAlpha <= 0f) {
                startAlpha = 0f;
                startFading = false;
                state = State.PLAY;
            }
        }

        // หน้าต่างดูเอกสารเฟดเข้า/ออก
        if (state == State.VIEW) viewerAlpha = Math.min(1f, viewerAlpha + 0.07f);
        else viewerAlpha = Math.max(0f, viewerAlpha - 0.07f);

        if (state == State.EVENT) runEvent(now);
    }

    private void runEvent(long now) {
        long t = now - phaseStart;
        switch (phase) {
            case 0: // รอ
                if (t >= T_WAIT_BEFORE) next(now);
                break;
            case 1: // เฟดเป็นดำ
                blackAlpha = clamp(t / (float) T_FADE_OUT);
                if (t >= T_FADE_OUT) {
                    blackAlpha = 1f;
                    showDoll = true;                       // สลับเป็นภาพตุ๊กตาใต้ฉากดำ
                    stopCry();
                    cryClip = playSound(SND_CRY);          // เสียงเด็กร้องไห้
                    next(now);
                }
                break;
            case 2: // เฟดสว่างเข้าภาพตุ๊กตา
                blackAlpha = 1f - clamp(t / (float) T_FADE_IN);
                if (t >= T_FADE_IN) { blackAlpha = 0f; next(now); }
                break;
            case 3: // ค้างภาพตุ๊กตา
                if (t >= T_DOLL_HOLD) next(now);
                break;
            case 4: // เฟดดำอีกครั้ง
                blackAlpha = clamp(t / (float) T_FADE_OUT);
                if (t >= T_FADE_OUT) {
                    blackAlpha = 1f;
                    showDoll = false;                      // กลับฉากเดิม (ใต้ฉากดำ)
                    stopCry();
                    next(now);
                }
                break;
            case 5: // ดำค้าง
                if (t >= T_BLACK_HOLD) next(now);
                break;
            case 6: // เฟดสว่างกลับฉากเดิม
                blackAlpha = 1f - clamp(t / (float) T_FADE_IN);
                if (t >= T_FADE_IN) {
                    blackAlpha = 0f;
                    state = State.PLAY;
                    if (onFinish != null) {                // จบฉาก 2 -> รอ 2 วิ -> ไปฉาก 3
                        Timer nextSceneTimer = new Timer(T_NEXT_SCENE, ev -> { stopScene(); onFinish.run(); });
                        nextSceneTimer.setRepeats(false);
                        nextSceneTimer.start();
                    }
                }
                break;
            default:
                break;
        }
    }

    private void next(long now) { phase++; phaseStart = now; }

    /** หยุดตัวจับเวลา/เสียงของฉากนี้ก่อนสลับไปฉากถัดไป */
    private void stopScene() {
        mainTimer.stop();
        stopCry();
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    // ---------------------------------------------------------------- วาด
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int pw = getWidth(), ph = getHeight();

        // คำนวณพื้นที่ภาพ (คงสัดส่วน)
        BufferedImage base = showDoll ? doll : room;
        double scale = Math.min(pw / (double) base.getWidth(), ph / (double) base.getHeight());
        dw = (int) (base.getWidth() * scale);
        dh = (int) (base.getHeight() * scale);
        dx = (pw - dw) / 2;
        dy = (ph - dh) / 2;

        // ฉากหลัก
        g.drawImage(base, dx, dy, dw, dh, null);

        // ไฮไลต์จุดคลิกเมื่อเอาเมาส์ชี้
        if (state == State.PLAY) {
            if (hoverCal) drawGlow(g, hotRect(HOT_CALENDAR));
            if (hoverDrawer) drawGlow(g, hotRect(HOT_DRAWER));
        }

        // ชื่อฉาก
        g.setFont(new Font("Tahoma", Font.PLAIN, Math.max(12, dw / 70)));
        g.setColor(new Color(216, 210, 196, 150));
        g.drawString("ฉากที่ 2 · ห้องนอนพ่อและแม่", dx + dw / 50, dy + dh / 25);

        // ข้อความด้านล่าง
        drawMessage(g);

        // เอกสารที่เปิดดู
        if (viewerAlpha > 0f && viewing != null) drawViewer(g, pw, ph);

        // ฉากดำสำหรับเฟด
        if (blackAlpha > 0f) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, blackAlpha));
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, pw, ph);
            g.setComposite(AlphaComposite.SrcOver);
        }

        // หน้าเริ่มเกม
        if (state == State.START || startAlpha > 0f) drawStart(g, pw, ph);

        g.dispose();
    }

    private void drawGlow(Graphics2D g, Rectangle2D.Double r) {
        g.setColor(new Color(255, 235, 190, 28));
        g.fill(new java.awt.geom.RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 8, 8));
        for (int i = 1; i <= 6; i++) {
            g.setColor(new Color(255, 235, 190, 40 - i * 6));
            g.setStroke(new BasicStroke(i * 2f));
            g.draw(new java.awt.geom.RoundRectangle2D.Double(r.x - i, r.y - i, r.width + 2 * i, r.height + 2 * i, 10, 10));
        }
    }

    private void drawViewer(Graphics2D g, int pw, int ph) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, viewerAlpha * 0.82f));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, pw, ph);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, viewerAlpha));
        double s = Math.min(pw * 0.9 / viewing.getWidth(), ph * 0.9 / viewing.getHeight());
        double zoom = 0.94 + 0.06 * viewerAlpha;              // ซูมเข้านิดๆ ตอนเปิด
        int w = (int) (viewing.getWidth() * s * zoom);
        int h = (int) (viewing.getHeight() * s * zoom);
        g.drawImage(viewing, (pw - w) / 2, (ph - h) / 2, w, h, null);

        g.setFont(new Font("Tahoma", Font.PLAIN, Math.max(12, pw / 75)));
        g.setColor(new Color(216, 210, 196, 140));
        String hint = "คลิกเพื่อปิด";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(hint, (pw - fm.stringWidth(hint)) / 2, ph - ph / 40);
        g.setComposite(AlphaComposite.SrcOver);
    }

    private void drawMessage(Graphics2D g) {
        long now = System.currentTimeMillis();
        if (message.isEmpty() || now < messageStart || now > messageEnd) return;
        long fadeMs = 1000;
        float a = 1f;
        if (now - messageStart < fadeMs) a = (now - messageStart) / (float) fadeMs;
        if (messageEnd - now < fadeMs) a = Math.min(a, (messageEnd - now) / (float) fadeMs);
        g.setFont(new Font("Tahoma", Font.PLAIN, Math.max(14, dw / 50)));
        FontMetrics fm = g.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(message)) / 2;
        int y = dy + dh - dh / 22;
        g.setColor(new Color(0, 0, 0, (int) (160 * a)));
        g.drawString(message, x + 2, y + 2);
        g.setColor(new Color(216, 210, 196, (int) (225 * a)));
        g.drawString(message, x, y);
    }

    private void drawStart(Graphics2D g, int pw, int ph) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp(startAlpha)));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, pw, ph);

        g.setColor(new Color(216, 210, 196));
        g.setFont(new Font("Tahoma", Font.PLAIN, Math.max(24, pw / 28)));
        FontMetrics fm = g.getFontMetrics();
        String t = "ห้องนอนพ่อและแม่";
        g.drawString(t, (pw - fm.stringWidth(t)) / 2, ph / 2);

        g.setColor(new Color(216, 210, 196, 150));
        g.setFont(new Font("Tahoma", Font.PLAIN, Math.max(13, pw / 70)));
        fm = g.getFontMetrics();
        String s = "คลิกเพื่อเริ่ม";
        g.drawString(s, (pw - fm.stringWidth(s)) / 2, ph / 2 + ph / 14);
        g.setComposite(AlphaComposite.SrcOver);
    }

    // ---------------------------------------------------------------- main
    /** เปิดหน้าต่างฉาก 2 ; พอจบจะสลับเป็นฉาก 3 ในหน้าต่างเดิม */
    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("บ้านเก่า - ฉากที่ 2");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            GameScene2 scene2 = new GameScene2();
            scene2.onFinish = () -> {
                GameScene3 scene3 = new GameScene3();
                f.setTitle("ฉากที่ 3 - ห้องนอน");
                f.setContentPane(scene3);
                f.revalidate();
                f.repaint();
                scene3.requestFocusInWindow();
            };
            f.setContentPane(scene2);
            f.pack();
            Dimension sc = Toolkit.getDefaultToolkit().getScreenSize();
            f.setLocation((sc.width - f.getWidth()) / 2, (sc.height - f.getHeight()) / 2);
            f.setVisible(true);
        });
    }

    public static void main(String[] args) {
        launch();
    }
}
