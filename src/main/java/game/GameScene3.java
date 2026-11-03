package game;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * ฉากที่ 3 : ห้องนอน (Point & Click) - Java Swing ล้วน
 *
 * โครงสร้างโฟลเดอร์ (วางไว้ข้าง Main.java):
 *   images/room.png     ภาพห้องนอน
 *   images/photo.png    ภาพถ่ายแม่กับลูก
 *   images/inside.png   ภาพในตู้เสื้อผ้า
 *   sounds/knock.wav    เสียงเคาะประตูรัว (ชุดเดียว)
 *   sounds/breath.wav   เสียงหายใจเบาๆ (จะเล่นวนซ้ำ)
 *   sounds/gunshot.wav  เสียงปืน 1 นัด
 *   sounds/cry.wav      เสียงร้องไห้เบาๆ
 *
 * คอมไพล์:  javac Main.java
 * รัน:      java Main
 * (Java รองรับไฟล์เสียง .wav / .aiff / .au โดยตรง ไม่รองรับ mp3)
 */
public class GameScene3 extends JPanel {

    enum State { START, EXPLORE, PHOTO, KNOCK, HIDE, INSIDE }

    // ---------- พิกัดจุดคลิก (อิงภาพขนาด 1448x1086) ----------
    static final double IW = 1448, IH = 1086;
    // กรอบรูปบนผนัง (เอียงเล็กน้อย) 4 มุม
    static final double[][] PHOTO_PTS = {{652, 352}, {735, 335}, {760, 445}, {680, 463}};
    // ตู้เสื้อผ้า (x, y, w, h เป็นสัดส่วน 0-1)
    static final double[] WARD = {0.0, 0.147, 0.276, 0.80};

    // ---------- ข้อมูลเกม ----------
    BufferedImage imgRoom, imgPhoto, imgInside;
    State state = State.START;
    boolean hover = false;
    boolean backVisible = false;

    // ข้อความ
    String subText = "";
    float subAlpha = 0, subTarget = 0;
    javax.swing.Timer subHideTimer;

    // ภาพ / ฉาก
    float photoAlpha = 0;
    float blackAlpha = 0;
    boolean fading = false;
    Runnable onFinish; // เชื่อมฉากถัดไปได้เมื่อมีการเพิ่มฉากใหม่
    boolean finishScheduled = false;

    // เสียง / ตัวจับเวลา
    final List<javax.swing.Timer> timers = new ArrayList<>();
    final List<Clip> loops = new ArrayList<>();
    javax.swing.Timer knockLoop;
    Clip knockClip; // เก็บเสียงเคาะที่กำลังเล่น เพื่อหยุดทันทีเมื่อเข้าตู้

    Font font;

    public GameScene3() {
        setBackground(Color.BLACK);
        imgRoom = load("images/room.png");
        imgPhoto = load("images/photo.png");
        imgInside = load("images/inside.png");
        font = pickThaiFont();

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { updateHover(e.getPoint()); }
            @Override public void mouseClicked(MouseEvent e) { onClick(e.getPoint()); }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);

        // ลูปอัปเดตภาพ ~33 fps
        new javax.swing.Timer(30, e -> tick()).start();
    }

    // =====================================================================
    //  ลำดับเหตุการณ์
    // =====================================================================

    void onClick(Point p) {
        switch (state) {
            case START:
                state = State.EXPLORE;
                break;

            case EXPLORE:
                if (inPhoto(p)) openPhoto();
                break;

            case PHOTO:
                if (backVisible && backRect().contains(p)) closePhoto();
                break;

            case HIDE:
                if (inWardrobe(p)) hideInWardrobe();
                break;

            default:
                break;
        }
    }

    /** 1) คลิกรูปบนผนัง -> ขึ้นรูปถ่าย + "นี่มันฉันนี่.." แล้วจางหาย */
    void openPhoto() {
        state = State.PHOTO;
        photoAlpha = 0;
        hover = false;
        later(900, () -> say("นี่มันฉันนี่..", 3000));
        later(1500, () -> backVisible = true);
    }

    /** 2) ออกจากรูป -> เสียงเคาะประตูรัว + "ลูกไปหาที่ซ่อนซะ" (ข้อความค้าง เสียงเคาะยังดัง) */
    void closePhoto() {
        state = State.KNOCK;
        backVisible = false;
        hideSub();
        later(700, () -> {
            playKnock(0f);
            later(1300, () -> say("แม่ : ลูกไปหาที่ซ่อนซะ", 0)); // 0 = แสดงค้างไว้
            later(2200, () -> {
                state = State.HIDE; // ตู้เสื้อผ้าเริ่มคลิกได้
                knockLoop = new javax.swing.Timer(2600, e -> playKnock(-4f));
                knockLoop.start();
            });
        });
    }

    /** เล่นเสียงเคาะโดยเก็บ Clip ไว้สำหรับหยุดเสียงได้ทันที */
    void playKnock(float volume) {
        if (state == State.INSIDE) return;
        stopKnockClip();
        knockClip = Snd.play("knock.wav", false, volume);
    }

    void stopKnockClip() {
        if (knockClip != null) {
            if (knockClip.isOpen()) {
                knockClip.stop();
                knockClip.close();
            }
            knockClip = null;
        }
    }

    void stopKnock() {
        if (knockLoop != null) {
            knockLoop.stop();
            knockLoop = null;
        }
        stopKnockClip();
    }

    /** 3) คลิกตู้เสื้อผ้า -> เข้าไปซ่อน -> เสียงหายใจ -> คำพูดคนร้าย -> ปืน -> "แม่.." -> เฟดจบ */
    void hideInWardrobe() {
        state = State.INSIDE;
        hover = false;
        stopKnock(); // หยุดทั้งตัวตั้งเวลาและเสียงเคาะที่กำลังเล่นทันที
        hideSub();

        later(900, () -> { Clip c = Snd.play("breath.wav", true, -6f); if (c != null) loops.add(c); });
        later(3500, () -> say("??? : ผัวของแก มันติดหนี้กูเป็นล้านๆ อย่าคิดว่าพวกมึงจะรอดกันไปได้ง่ายๆ", 0));
        later(9500, () -> { hideSub(); stopLoops(); });
        later(11000, () -> Snd.play("gunshot.wav", false, 0f));
        later(12600, () -> Snd.play("cryw.wav", false, -6f));
        later(13400, () -> say("แม่..", 3500));
        later(17000, () -> fading = true);
    }

    // =====================================================================
    //  ตัวช่วย: เวลา / ข้อความ / เสียง
    // =====================================================================

    void later(int ms, Runnable r) {
        javax.swing.Timer t = new javax.swing.Timer(ms, e -> r.run());
        t.setRepeats(false);
        timers.add(t);
        t.start();
    }

    /** แสดงข้อความ holdMs มิลลิวินาทีแล้วจางหาย (holdMs <= 0 = ค้างไว้) */
    void say(String text, int holdMs) {
        if (subHideTimer != null) subHideTimer.stop();
        subText = text;
        subTarget = 1;
        if (holdMs > 0) {
            subHideTimer = new javax.swing.Timer(holdMs, e -> subTarget = 0);
            subHideTimer.setRepeats(false);
            subHideTimer.start();
        }
    }

    void hideSub() {
        if (subHideTimer != null) subHideTimer.stop();
        subTarget = 0;
    }

    void stopLoops() {
        for (Clip c : loops) { c.stop(); c.close(); }
        loops.clear();
    }

    void tick() {
        // ข้อความ: ขึ้นเร็ว จางช้า
        if (subAlpha < subTarget) subAlpha = Math.min(subTarget, subAlpha + 0.08f);
        else if (subAlpha > subTarget) subAlpha = Math.max(subTarget, subAlpha - 0.025f);
        // รูปถ่ายค่อยๆ ปรากฏ
        if (state == State.PHOTO && photoAlpha < 1) photoAlpha = Math.min(1, photoAlpha + 0.02f);
        // เฟดดำตอนจบ
        if (fading && blackAlpha < 1) blackAlpha = Math.min(1, blackAlpha + 0.012f);
        if (blackAlpha >= 1 && !finishScheduled) {
            finishScheduled = true;
            later(2000, () -> {
                stopLoops();
                stopKnock();
                if (onFinish != null) onFinish.run();
            });
        }
        repaint();
    }

    // =====================================================================
    //  จุดคลิก
    // =====================================================================

    Rectangle imageRect() {
        BufferedImage bg = (state == State.INSIDE) ? imgInside : imgRoom;
        return fit(bg.getWidth(), bg.getHeight(), getWidth(), getHeight());
    }

    Polygon photoPoly() {
        Rectangle r = imageRect();
        Polygon poly = new Polygon();
        for (double[] p : PHOTO_PTS)
            poly.addPoint((int) (r.x + p[0] / IW * r.width), (int) (r.y + p[1] / IH * r.height));
        return poly;
    }

    Rectangle wardRect() {
        Rectangle r = imageRect();
        return new Rectangle((int) (r.x + WARD[0] * r.width), (int) (r.y + WARD[1] * r.height),
                (int) (WARD[2] * r.width), (int) (WARD[3] * r.height));
    }

    boolean inPhoto(Point p) { return photoPoly().contains(p); }
    boolean inWardrobe(Point p) { return wardRect().contains(p); }

    Rectangle backRect() { return new Rectangle(getWidth() - 110, 20, 90, 36); }

    void updateHover(Point p) {
        boolean h = (state == State.EXPLORE && inPhoto(p))
                || (state == State.HIDE && inWardrobe(p))
                || (state == State.PHOTO && backVisible && backRect().contains(p));
        hover = h;
        setCursor(Cursor.getPredefinedCursor(h ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    // =====================================================================
    //  วาดภาพ
    // =====================================================================

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int W = getWidth(), H = getHeight();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, W, H);

        if (state == State.START) {
            g.setColor(new Color(233, 226, 211));
            g.setFont(font.deriveFont(Font.BOLD, Math.max(26f, H / 14f)));
            drawCentered(g, "ฉากที่ 3 · ห้องนอน", W / 2, H / 2 - 20);
            g.setFont(font.deriveFont(Math.max(15f, H / 40f)));
            g.setColor(new Color(233, 226, 211, 150));
            drawCentered(g, "คลิกเพื่อเริ่ม · คลิกสิ่งของที่น่าสนใจในห้อง", W / 2, H / 2 + 30);
            g.dispose();
            return;
        }

        // พื้นหลัง
        BufferedImage bg = (state == State.INSIDE) ? imgInside : imgRoom;
        Rectangle r = imageRect();
        g.drawImage(bg, r.x, r.y, r.width, r.height, null);

        // ไฮไลต์ตอนเมาส์ชี้ / ตู้เรืองแสงเบาๆ เป็นใบ้
        if (state == State.EXPLORE && hover) {
            Polygon p = photoPoly();
            g.setColor(new Color(233, 226, 211, 30));
            g.fillPolygon(p);
            g.setColor(new Color(233, 226, 211, 110));
            g.drawPolygon(p);
        }
        if (state == State.HIDE) {
            Rectangle w = wardRect();
            double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 380.0);
            int a = hover ? 40 : (int) (8 + 14 * pulse);
            g.setColor(new Color(233, 226, 211, a));
            g.fillRect(w.x, w.y, w.width, w.height);
            if (hover) {
                g.setColor(new Color(233, 226, 211, 110));
                g.drawRect(w.x, w.y, w.width, w.height);
            }
        }

        // หน้าดูรูปถ่าย
        if (state == State.PHOTO) {
            g.setColor(new Color(0, 0, 0, 235));
            g.fillRect(0, 0, W, H);
            Rectangle pr = fit(imgPhoto.getWidth(), imgPhoto.getHeight(), (int) (W * 0.88), (int) (H * 0.84));
            pr.x = (W - pr.width) / 2;
            pr.y = (H - pr.height) / 2;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, photoAlpha));
            g.drawImage(imgPhoto, pr.x, pr.y, pr.width, pr.height, null);
            g.setComposite(AlphaComposite.SrcOver);

            if (backVisible) {
                Rectangle b = backRect();
                g.setColor(new Color(0, 0, 0, hover ? 120 : 0));
                g.fillRoundRect(b.x, b.y, b.width, b.height, 6, 6);
                g.setColor(new Color(233, 226, 211, hover ? 255 : 190));
                g.drawRoundRect(b.x, b.y, b.width, b.height, 6, 6);
                g.setFont(font.deriveFont(16f));
                drawCentered(g, "กลับ", b.x + b.width / 2, b.y + 24);
            }
        }

        drawSubtitle(g, W, H);

        // เฟดดำตอนจบ
        if (blackAlpha > 0) {
            g.setColor(new Color(0, 0, 0, Math.round(255 * blackAlpha)));
            g.fillRect(0, 0, W, H);
        }
        g.dispose();
    }

    void drawSubtitle(Graphics2D g, int W, int H) {
        if (subAlpha <= 0.01f || subText.isEmpty()) return;
        float size = Math.max(18f, H / 26f);
        g.setFont(font.deriveFont(size));
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = wrap(subText, fm, (int) (W * 0.84));
        int lh = (int) (fm.getHeight() * 1.2);
        int y = H - (int) (H * 0.07) - (lines.size() - 1) * lh;
        int a = Math.round(255 * subAlpha);
        for (String ln : lines) {
            int x = (W - fm.stringWidth(ln)) / 2;
            g.setColor(new Color(0, 0, 0, a));
            g.drawString(ln, x + 2, y + 2);
            g.setColor(new Color(233, 226, 211, a));
            g.drawString(ln, x, y);
            y += lh;
        }
    }

    // =====================================================================
    //  ฟังก์ชันช่วยทั่วไป
    // =====================================================================

    static Rectangle fit(int iw, int ih, int bw, int bh) {
        double s = Math.min((double) bw / iw, (double) bh / ih);
        int w = (int) (iw * s), h = (int) (ih * s);
        return new Rectangle((bw - w) / 2, (bh - h) / 2, w, h);
    }

    static void drawCentered(Graphics2D g, String s, int cx, int baseY) {
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, baseY);
    }

    /** ตัดบรรทัด: ตัดที่ช่องว่างถ้ามี ไม่งั้นตัดตามตัวอักษร (ภาษาไทยไม่มีเว้นวรรค) */
    static List<String> wrap(String text, FontMetrics fm, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            cur.append(text.charAt(i));
            if (fm.stringWidth(cur.toString()) > maxW) {
                int sp = cur.lastIndexOf(" ");
                if (sp > 0) {
                    out.add(cur.substring(0, sp));
                    cur = new StringBuilder(cur.substring(sp + 1));
                } else {
                    out.add(cur.substring(0, cur.length() - 1));
                    cur = new StringBuilder(cur.substring(cur.length() - 1));
                }
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    static BufferedImage load(String path) {
        try {
            return GameScene2.readGameImage(path);
        } catch (java.io.IOException e) {
            JOptionPane.showMessageDialog(null, "โหลดรูปฉากที่ 3 ไม่สำเร็จ:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            throw new IllegalStateException("Cannot load scene 3 image: " + path, e);
        }
    }

    static Font pickThaiFont() {
        String[] want = {"Leelawadee UI", "Tahoma", "Thonburi", "Noto Sans Thai", "Sarabun", "Loma"};
        java.util.Set<String> have = new java.util.HashSet<>(
                java.util.Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String f : want) if (have.contains(f)) return new Font(f, Font.PLAIN, 24);
        return new Font(Font.DIALOG, Font.PLAIN, 24);
    }

    // =====================================================================
    //  เสียง (โหลดจากโฟลเดอร์ sounds/ ถ้าไม่มีไฟล์จะข้ามไปเฉยๆ)
    // =====================================================================

    static class Snd {
    static Clip play(String name, boolean loop, float gainDb) {
        try {
            java.net.URL url = GameScene3.class.getResource(
                "/assets/sounds/" + name
            );

            if (url == null) {
                System.out.println("ไม่พบไฟล์เสียง: " + name);
                return null;
            }

            try (AudioInputStream in =
                     AudioSystem.getAudioInputStream(url)) {

                Clip c = AudioSystem.getClip();
                c.open(in);

                if (c.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl fc =
                        (FloatControl) c.getControl(
                            FloatControl.Type.MASTER_GAIN
                        );

                    fc.setValue(Math.max(fc.getMinimum(),
                        Math.min(fc.getMaximum(), gainDb)));
                }

                if (loop) {
                    c.loop(Clip.LOOP_CONTINUOUSLY);
                } else {
                    c.addLineListener(e -> {
                        if (e.getType() == LineEvent.Type.STOP) {
                            c.close();
                        }
                    });
                    c.start();
                }

                return c;
            }

        } catch (Exception e) {
            System.out.println("เล่นเสียงไม่ได้: " + name);
            e.printStackTrace();
            return null;
        }
    }
}

    // =====================================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("ฉากที่ 3 - ห้องนอน");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(new GameScene3());
            f.setSize(1100, 860);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}
