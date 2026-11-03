package game;

import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** ฉากที่ 2 – ห้องส่วนกลาง (Point & Click) : JavaFX  (เรียกจาก Main ด้วย new GameScene(stage).show()) */
public class GameScene {

    // ===== ชื่อไฟล์ภาพ (วางไว้ที่ resources/assets/images/ ข้างๆ lobby.png) =====
    static final String IMG_SCENE1 = "scene1.png";   // ภาพที่ 1 พื้นหลัง
    static final String IMG_FAM1   = "family1.png";  // รูปครอบครัว
    static final String IMG_FAM2   = "family2.jpg";  // รูปครอบครัวเปื้อนเลือด
    static final String IMG_SCENE3 = "scene3.png";   // ภาพที่ 3 แม่นอนคว่ำ
    // ไฟล์เสียงจริง (ไม่บังคับ): ถ้ามีจะใช้แทนเสียงสังเคราะห์
    static final String VOICE_FILE  = "sounds/help.wav";
    static final String NEWS_FILE   = "sounds/news.wav";
    static final String SCREAM_FILE = "sounds/scream.wav";
    static final String SHOT_FILE   = "sounds/shot.wav";

    static final double ASPECT = 1448.0 / 1086.0;
    static final Random RND = new Random();

    // พื้นที่คลิก (เปอร์เซ็นต์ของภาพ: left, top, width, height)
    static final double[][] HS = {
        {9.0, 56.6, 15.9, 8.0},    // 0 จาน
        {39.6, 52.5, 12.9, 6.0},   // 1 จาน
        {25.5, 67.2, 20.4, 13.4},  // 2 จาน
        {66.5, 29.3, 12.2, 9.2},   // 3 วิทยุ
        {8.3, 23.5, 24.0, 27.0}    // 4 กรอบรูป
    };
    static final int PLATE_LAST = 2, RADIO = 3, FRAMES = 4;

    final Stage stage;
    final Canvas canvas = new Canvas(1000, 750);
    final Button replay = new Button("เล่นใหม่");
    Image scene1, scene3, fam1, fam2, bg, famCur;
    int story;   // ลำดับเนื้อเรื่อง 0-4 (ดูใน click())
    boolean photoShow, bloody, done, busy, plateBroken, endShow, debug;
    long photoAt, msgAt, flashAt = -1, redAt = -1, shakeAt = -1;
    String msg = ""; int msgMs;
    int hover = -1, gen = 0;
    final List<Frag> frags = new ArrayList<>();
    final List<Chip> chips = new ArrayList<>();
    long breakAt, lastNs;
    WritableImage patch;     // แผ่นลายโต๊ะปิดตำแหน่งจานเดิม
    AnimationTimer loop;     // ลูปวาดภาพ (เก็บไว้เพื่อหยุดตอนเปลี่ยนฉาก)

    /** ชิ้นส่วนจานแตก (ตัดจากภาพจานจริง) */
    static class Frag {
        double[] rx, ry;          // มุมของชิ้น (สัดส่วนของกรอบจาน, สัมพันธ์กับจุดกึ่งกลางชิ้น)
        double cx, cy;            // จุดกึ่งกลางชิ้น (สัดส่วนของกรอบจาน)
        double ox, oy, vx, vy;    // การเคลื่อนที่ (หน่วย = ความกว้างภาพ)
        double rot, vr, restOy;   // การหมุน (องศา) และระดับที่ชิ้นหยุด
        double shade;             // ความมืดของชิ้น (จำลองการเอียงรับแสงต่างกัน)
        void step(double dt) {
            vy += 1.6 * dt; ox += vx * dt; oy += vy * dt; rot += vr * dt;
            if (oy > restOy) {                       // ตกกระทบโต๊ะ: เด้งเล็กน้อยแล้วหยุด ไม่กระโดดซ้ำ
                oy = restOy;
                vy = Math.abs(vy) > .12 ? -vy * .12 : 0;
                vx *= .45; vr *= .4;
            }
            if (vy == 0) { double k = Math.exp(-9 * dt); vx *= k; vr *= k; }   // ไถลช้าลงจนหยุด
        }
    }
    /** เศษเล็กๆ กระเด็น */
    static class Chip { double x, y, vx, vy, size, life; }

    // กรอบจานใบหน้าสุดในภาพที่ 1 (เปอร์เซ็นต์: left, top, width, height)
    static final double[] PL = {25.5, 67.2, 20.4, 13.4};

    public GameScene(Stage stage) {
        this.stage = stage;
        scene1 = load(IMG_SCENE1); scene3 = load(IMG_SCENE3);
        fam1 = load(IMG_FAM1); fam2 = load(IMG_FAM2);
        reset();
    }

    /** แสดงฉากนี้บน Stage */
    public void show() {
        StackPane root = new StackPane(canvas, replay);
        root.setStyle("-fx-background-color: black;");
        canvas.widthProperty().bind(root.widthProperty());
        canvas.heightProperty().bind(root.heightProperty());
        canvas.setCursor(Cursor.CROSSHAIR);

        replay.setVisible(false);
        replay.setFocusTraversable(false);
        replay.setStyle("-fx-background-color:transparent;-fx-border-color:#888;-fx-text-fill:#ddd;"
            + "-fx-font-size:15px;-fx-padding:6 18 6 18;-fx-cursor:hand;");
        StackPane.setAlignment(replay, Pos.CENTER);
        replay.setTranslateY(45);
        replay.setOnAction(e -> reset());

        canvas.setOnMousePressed(e -> click(e.getX(), e.getY()));
        canvas.setOnMouseMoved(e -> hover = hit(e.getX(), e.getY()));

        Scene sc = new Scene(root, 1000, 750, Color.BLACK);
        sc.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.H) debug = !debug; });

        stage.setTitle("ฉากที่ 2 – ห้องส่วนกลาง");
        stage.setScene(sc);
        stage.setResizable(true);
        stage.centerOnScreen();
        stage.show();

        loop = new AnimationTimer() {
            @Override public void handle(long t) { paint(); }
        };
        loop.start();
    }

    // ===== โหลดภาพ : ลอง resources ก่อน แล้วค่อยโฟลเดอร์ images/ =====
    static Image load(String name) {
        for (String p : new String[]{"/assets/images/" + name, "/images/" + name}) {
            InputStream in = GameScene.class.getResourceAsStream(p);
            if (in != null) return new Image(in);
        }
        File f = new File("images/" + name);
        if (f.exists()) return new Image(f.toURI().toString());
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setHeaderText(null);
        a.setContentText("หาไฟล์ภาพไม่เจอ: /assets/images/" + name);
        a.show();
        return new WritableImage(10, 10);
    }

    void reset() {
        gen++;
        bg = scene1; famCur = fam1;
        story = 0;
        photoShow = bloody = done = busy = plateBroken = endShow = false;
        flashAt = redAt = shakeAt = -1; msg = ""; msgAt = 0;
        frags.clear(); chips.clear(); breakAt = 0; patch = null;
        replay.setVisible(false);
    }

    void later(int ms, Runnable r) {
        final int g = gen;
        PauseTransition p = new PauseTransition(Duration.millis(ms));
        p.setOnFinished(e -> { if (g == gen) r.run(); });
        p.play();
    }

    void say(String t, int ms) { msg = t; msgAt = System.currentTimeMillis(); msgMs = ms; }

    // ===== พื้นที่ภาพ (คงสัดส่วน) : {x, y, w, h} =====
    double[] stageRect() {
        double w = canvas.getWidth(), h = canvas.getHeight();
        double sw = w, sh = w / ASPECT;
        if (sh > h) { sh = h; sw = h * ASPECT; }
        return new double[]{(w - sw) / 2, (h - sh) / 2, sw, sh};
    }

    int hit(double mx, double my) {
        if (busy || photoShow) return -1;
        double[] s = stageRect();
        double fx = (mx - s[0]) * 100.0 / s[2], fy = (my - s[1]) * 100.0 / s[3];
        for (int i = 0; i < HS.length; i++) {
            if (i == 2 && plateBroken) continue;
            double[] r = HS[i];
            if (fx >= r[0] && fx <= r[0] + r[2] && fy >= r[1] && fy <= r[1] + r[3]) return i;
        }
        return -1;
    }

    // ===== การคลิก =====
    void click(double mx, double my) {
        if (photoShow) {                       // คลิกที่ภาพ = ปิดภาพ
            photoShow = false;
            if (bloody && !done) { done = true; later(450, this::sequence); }
            else if (story == 2) story = 3;     // ดูรูปครั้งแรกเสร็จ -> ปลดล็อกเสียงข่าวที่วิทยุ
            return;
        }
        if (busy) return;
        int k = hit(mx, my);
        if (k < 0) return;
        if (k <= PLATE_LAST) {
            say("มีจานสามใบ", 2200);
            if (story == 0) story = 1;                 // 1. คลิกจานก่อน
        } else if (k == RADIO) {
            Sound.play(Sound.noise(.7, .35, "bp", 1800, 0));
            if (story >= 3) {                          // 4. วิทยุเปลี่ยนเป็นเสียงข่าว
                if (story == 3) story = 4;
                later(500, () -> voice("ช่วยพ่อด้วย..", NEWS_FILE));
                say("\"ช่วยพ่อด้วย..\"", 4500);
            } else {                                   // 2. วิทยุครั้งแรก = "ช่วยพ่อด้วย.."
                if (story == 1) story = 2;
                
                say("\"ในปี $%@# เกิดเหตุฆาตกรรมยกครัว\"", 3200);
            }
        } else if (k == FRAMES) {
            if (story >= 4) { famCur = fam2; bloody = true; }   // 5. รูปเปลี่ยนเป็นภาพเปื้อนเลือด
            photoShow = true; photoAt = System.currentTimeMillis();
        }
    }

    // ===== เหตุการณ์ต่อเนื่อง =====
    void sequence() {
        busy = true; breakPlate();
        later(500, () -> { if (new File(SCREAM_FILE).exists()) Sound.playFile(SCREAM_FILE); else Sound.play(Sound.scream()); });
        later(1500, () -> {
            if (new File(SHOT_FILE).exists()) Sound.playFile(SHOT_FILE); else Sound.play(Sound.shot());
            flashAt = System.currentTimeMillis(); bg = scene3;   // เศษจานยังอยู่ต่อจนจบฉาก
        });
        later(1950, () -> { Sound.play(Sound.thud()); shakeAt = System.currentTimeMillis(); redAt = System.currentTimeMillis(); });
        later(3300, () -> say("...พ่อ!!", 3000));
        later(6500, () -> {
            endShow = true;
            later(2000, this::goNextScene);   // เปลี่ยนฉากอัตโนมัติภายใน 2 วินาที
        });
    }

    /** ปิดฉาก 1 (JavaFX) แล้วเปิดฉาก 2 (Swing) */
    void goNextScene() {
        if (loop != null) loop.stop();
        javax.swing.SwingUtilities.invokeLater(GameScene2::launch);
        stage.hide();
    }

    static List<double[]> clipHalf(List<double[]> poly, double nx, double ny, double c) {
        List<double[]> out = new ArrayList<>(); int n = poly.size();
        for (int i = 0; i < n; i++) {
            double[] a = poly.get(i), b = poly.get((i + 1) % n);
            double da = nx * a[0] + ny * a[1] - c, db = nx * b[0] + ny * b[1] - c;
            if (da <= 0) out.add(a);
            if (da * db < 0) { double t = da / (da - db); out.add(new double[]{a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1])}); }
        }
        return out;
    }

    /** สร้างแผ่นลายโต๊ะ (โคลนจากพื้นโต๊ะใต้จาน ขอบจางเนียน) ไว้ปิดตำแหน่งจานเดิม ไม่ให้เห็นเงาดำ */
    WritableImage makePatch() {
        try {
            PixelReader pr = scene1.getPixelReader();
            if (pr == null) return null;
            double iw = scene1.getWidth(), ih = scene1.getHeight();
            int w = (int) (iw * PL[2] / 100 * PATCH_EX), hh = (int) (ih * PL[3] / 100 * PATCH_EX);
            int srcX = (int) (iw * (PL[0] + PL[2] / 2) / 100 - w / 2.0);
            int srcY = (int) Math.min(ih - hh - 2, ih * .85);
            WritableImage out = new WritableImage(w, hh);
            PixelWriter pw = out.getPixelWriter();
            for (int y = 0; y < hh; y++) for (int x = 0; x < w; x++) {
                double dx = (x + .5 - w / 2.0) / (w / 2.0), dy = (y + .5 - hh / 2.0) / (hh / 2.0);
                double d = Math.sqrt(dx * dx + dy * dy);
                double a = d < .88 ? 1 : d > 1 ? 0 : 1 - (d - .88) / .12;
                a = a * a * (3 - 2 * a);
                int argb = pr.getArgb(srcX + x, srcY + y);
                pw.setArgb(x, y, ((int) (a * 255) << 24) | (argb & 0xFFFFFF));
            }
            return out;
        } catch (Exception ex) { return null; }
    }
    static final double PATCH_EX = 1.22;

    static List<double[]> jag(List<double[]> poly) {      // ทำขอบเศษให้หยักไม่เรียบ
        List<double[]> j = new ArrayList<>(); int n = poly.size();
        for (int i = 0; i < n; i++) {
            double[] a = poly.get(i), b = poly.get((i + 1) % n);
            j.add(a);
            double ex = b[0] - a[0], ey = b[1] - a[1], L = Math.hypot(ex, ey);
            if (L > .06) {
                double t = .35 + RND.nextDouble() * .3, off = (RND.nextDouble() - .5) * L * .22;
                j.add(new double[]{a[0] + ex * t - ey / L * off, a[1] + ey * t + ex / L * off});
            }
        }
        return j;
    }

    /** จานแตกจริง: จานแตกเป็นเศษเหลี่ยมหยักๆ ส่วนใหญ่ร่วงอยู่ใกล้ที่เดิม กระเด็นออกเล็กน้อย แล้วนิ่งบนโต๊ะ */
    void breakPlate() {
        Sound.play(Sound.plate());
        plateBroken = true;
        breakAt = System.currentTimeMillis(); lastNs = 0;
        frags.clear(); chips.clear();
        patch = makePatch();
        List<double[]> seeds = new ArrayList<>();
        seeds.add(new double[]{.5 + (RND.nextDouble() - .5) * .08, .5 + (RND.nextDouble() - .5) * .08});
        for (int tries = 0; tries < 600 && seeds.size() < 28; tries++) {
            double r = Math.sqrt(RND.nextDouble()) * .52, a = RND.nextDouble() * Math.PI * 2;
            double[] c = {.5 + Math.cos(a) * r, .5 + Math.sin(a) * r};
            boolean ok = true;
            for (double[] o : seeds) if (Math.hypot(o[0] - c[0], o[1] - c[1]) < .095) { ok = false; break; }
            if (ok) seeds.add(c);
        }
        for (double[] si : seeds) {
            List<double[]> poly = new ArrayList<>();
            poly.add(new double[]{0, 0}); poly.add(new double[]{1, 0}); poly.add(new double[]{1, 1}); poly.add(new double[]{0, 1});
            for (double[] sj : seeds) {
                if (sj == si) continue;
                poly = clipHalf(poly, 2 * (sj[0] - si[0]), 2 * (sj[1] - si[1]),
                    sj[0] * sj[0] + sj[1] * sj[1] - si[0] * si[0] - si[1] * si[1]);
                if (poly.size() < 3) break;
            }
            if (poly.size() < 3) continue;
            for (double[] p : poly) {
                double dx = (p[0] - .5) / .5, dy = (p[1] - .5) / .5, d = Math.sqrt(dx * dx + dy * dy);
                if (d > 1) { p[0] = .5 + (p[0] - .5) / d; p[1] = .5 + (p[1] - .5) / d; }
            }
            poly = jag(poly);
            double mx = 0, my = 0;
            for (double[] p : poly) { mx += p[0]; my += p[1]; }
            Frag f = new Frag(); int n = poly.size();
            f.cx = mx / n; f.cy = my / n; f.rx = new double[n]; f.ry = new double[n];
            for (int i = 0; i < n; i++) { f.rx[i] = poly.get(i)[0] - f.cx; f.ry[i] = poly.get(i)[1] - f.cy; }
            double dxu = (f.cx - .5) * PL[2] / 100, dyu = (f.cy - .5) * PL[3] / 100 * .75;
            double len = Math.hypot(dxu, dyu);
            if (len < 1e-4) { double a = RND.nextDouble() * 6.28; dxu = Math.cos(a); dyu = Math.sin(a); len = 1; }
            double speed = .15 + RND.nextDouble() * .17;               // กระเด็นออกเบาๆ
            f.vx = dxu / len * speed + (RND.nextDouble() - .5) * .04;
            f.vy = dyu / len * speed * .5 - (.05 + RND.nextDouble() * .13);
            f.vr = (RND.nextBoolean() ? 1 : -1) * (60 + RND.nextDouble() * 220);
            f.restOy = RND.nextDouble() * .005;
            f.shade = RND.nextDouble() * .22;
            frags.add(f);
        }
        for (int i = 0; i < 24; i++) {                                 // ผงเศษละเอียด
            Chip c = new Chip(); double a = RND.nextDouble() * 6.28, sp = .06 + RND.nextDouble() * .25;
            c.x = (RND.nextDouble() - .5) * .1; c.y = (RND.nextDouble() - .5) * .04;
            c.vx = Math.cos(a) * sp; c.vy = Math.sin(a) * sp * .4 - .08 - RND.nextDouble() * .15;
            c.size = 1 + RND.nextDouble() * 1.5; c.life = .35 + RND.nextDouble() * .4;
            chips.add(c);
        }
    }

    static void polyPath(GraphicsContext g, Frag f, double plw, double plh) {
        g.beginPath();
        for (int i = 0; i < f.rx.length; i++) {
            double X = f.rx[i] * plw, Y = f.ry[i] * plh;
            if (i == 0) g.moveTo(X, Y); else g.lineTo(X, Y);
        }
        g.closePath();
    }

    // ===== เสียงพูด : ใช้ไฟล์ .wav ถ้ามี ไม่งั้นลองใช้ TTS ของระบบ =====
    void voice(String text, String file) {
        if (new File(file).exists()) { Sound.playFile(file); return; }
        new Thread(() -> {
            try {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("mac")) new ProcessBuilder("say", "-v", "Kanya", text).start();
                else if (os.contains("win")) new ProcessBuilder("powershell", "-Command",
                    "Add-Type -AssemblyName System.Speech;$s=New-Object System.Speech.Synthesis.SpeechSynthesizer;"
                    + "$v=$s.GetInstalledVoices()|?{$_.VoiceInfo.Culture.Name -like 'th*'}|select -First 1;"
                    + "if($v){$s.SelectVoice($v.VoiceInfo.Name)};$s.Rate=-3;$s.Speak('" + text + "')").start();
                else new ProcessBuilder("espeak", "-v", "th", text).start();
            } catch (Exception ignored) { }
        }).start();
    }

    // ===== วาดภาพ =====
    void paint() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double W = canvas.getWidth(), H = canvas.getHeight();
        g.setFill(Color.BLACK); g.fillRect(0, 0, W, H);
        double[] s = stageRect();
        double sx = s[0], sy = s[1], sw = s[2], sh = s[3];
        long now = System.currentTimeMillis();

        g.save();
        g.beginPath(); g.rect(sx, sy, sw, sh); g.closePath(); g.clip();
        // สั่นจอ
        if (shakeAt > 0 && now - shakeAt < 500) {
            double p = (now - shakeAt) / 500.0;
            double[][] kf = {{0,0,0},{.2,-8,5},{.4,7,-6},{.6,-5,4},{.8,3,-2},{1,0,0}};
            for (int i = 0; i < kf.length - 1; i++) if (p >= kf[i][0] && p <= kf[i+1][0]) {
                double u = (p - kf[i][0]) / (kf[i+1][0] - kf[i][0]);
                g.translate(kf[i][1] + (kf[i+1][1] - kf[i][1]) * u, kf[i][2] + (kf[i+1][2] - kf[i][2]) * u);
            }
        }
        g.drawImage(bg, sx, sy, sw, sh);

        // hover + debug
        for (int i = 0; i < HS.length; i++) {
            if (i == 2 && plateBroken) continue;
            double[] r = HS[i];
            double rx = sx + sw * r[0] / 100, ry = sy + sh * r[1] / 100, rw = sw * r[2] / 100, rh = sh * r[3] / 100;
            if (debug) {
                g.setFill(Color.rgb(255, 80, 80, 50 / 255.0)); g.fillRoundRect(rx, ry, rw, rh, 12, 12);
                g.setStroke(Color.rgb(255, 85, 85)); g.setLineWidth(2); g.setLineDashes(6, 4);
                g.strokeRoundRect(rx, ry, rw, rh, 12, 12); g.setLineDashes(null);
            } else if (i == hover) {
                g.setFill(Color.rgb(255, 240, 200, 22 / 255.0)); g.fillRoundRect(rx, ry, rw, rh, 12, 12);
            }
        }

        // จานแตก
        double dtp = lastNs == 0 ? 0 : Math.min(.05, (System.nanoTime() - lastNs) / 1e9);
        lastNs = System.nanoTime();
        if (!frags.isEmpty()) {
            double plx = sx + sw * PL[0] / 100, ply = sy + sh * PL[1] / 100, plw = sw * PL[2] / 100, plh = sh * PL[3] / 100;
            double age = (now - breakAt) / 1000.0;
            boolean moving = age >= .12;                 // 0.12 วินาทีแรก: เห็นรอยร้าวก่อน แล้วค่อยแตก
            if (moving && patch != null)                 // ลายโต๊ะปิดที่เดิมของจาน (ไม่มีเงาดำ)
                g.drawImage(patch, plx + plw / 2 - plw * PATCH_EX / 2, ply + plh / 2 - plh * PATCH_EX / 2,
                            plw * PATCH_EX, plh * PATCH_EX);
            for (Frag f : frags) {
                if (moving) f.step(dtp);
                double bx = plx + f.cx * plw + f.ox * sw, by = ply + f.cy * plh + f.oy * sw;
                if (moving) {                            // เงาใต้เศษ ให้ดูมีความหนา
                    g.save(); g.translate(bx + 1.5, by + 2.5); g.rotate(f.rot);
                    polyPath(g, f, plw, plh); g.setFill(Color.rgb(0, 0, 0, .55)); g.fill();
                    g.restore();
                }
                g.save();
                g.translate(bx, by); g.rotate(f.rot);
                g.save();
                polyPath(g, f, plw, plh); g.clip();
                g.drawImage(scene1, scene1.getWidth() * PL[0] / 100, scene1.getHeight() * PL[1] / 100,
                    scene1.getWidth() * PL[2] / 100, scene1.getHeight() * PL[3] / 100,
                    -f.cx * plw, -f.cy * plh, plw, plh);
                if (moving) { g.setFill(Color.rgb(0, 0, 0, f.shade)); g.fillRect(-plw, -plh, plw * 2, plh * 2); }
                g.restore();
                polyPath(g, f, plw, plh);
                g.setStroke(moving ? Color.rgb(0, 0, 0, .35) : Color.rgb(0, 0, 0, .8));
                g.setLineWidth(moving ? .8 : 1.6); g.stroke();
                g.restore();
            }
            if (moving) for (Chip c : chips) {
                c.life -= dtp; if (c.life <= 0) continue;
                c.vy += 1.6 * dtp; c.x += c.vx * dtp; c.y += c.vy * dtp;
                g.setFill(Color.rgb(185, 185, 178, Math.min(1, c.life * 2)));
                g.fillRect(plx + plw / 2 + c.x * sw, ply + plh / 2 + c.y * sw, c.size, c.size);
            }
        }

        // ขอบจอแดง
        if (redAt > 0) {
            double a = Math.min(1, (now - redAt) / 1500.0);
            double rad = Math.hypot(sw / 2.0, sh / 2.0);
            RadialGradient rg = new RadialGradient(0, 0, sx + sw / 2, sy + sh / 2, rad, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(120, 0, 0, 0)), new Stop(.4, Color.rgb(120, 0, 0, 0)),
                new Stop(1, Color.rgb(120, 0, 0, 140 * a / 255.0)));
            g.setFill(rg); g.fillRect(sx - 20, sy - 20, sw + 40, sh + 40);
        }

        // ข้อความ
        if (msg.length() > 0) {
            long dt = now - msgAt; double a;
            if (dt < 150) a = dt / 150.0; else if (dt < msgMs) a = 1; else a = Math.max(0, 1 - (dt - msgMs) / 800.0);
            if (a > 0) {
                g.setFont(Font.font("Tahoma", Math.max(16, Math.min(30, sw / 38))));
                g.setTextAlign(TextAlignment.CENTER);
                double x = sx + sw / 2, y = sy + sh * .94;
                g.setFill(Color.rgb(0, 0, 0, 200 * a / 255.0));
                for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) g.fillText(msg, x + dx, y + dy);
                g.setFill(Color.rgb(238, 238, 238, a)); g.fillText(msg, x, y);
            }
        }

        // ภาพครอบครัว
        if (photoShow) {
            g.setFill(Color.rgb(0, 0, 0, 199 / 255.0)); g.fillRect(sx - 20, sy - 20, sw + 40, sh + 40);
            double p = Math.min(1, (now - photoAt) / 800.0), e = 1 - Math.pow(1 - p, 4);
            double iw = sw * .56, ih = iw * famCur.getHeight() / famCur.getWidth();
            double top1 = sy + sh * .5 - ih * .55, top0 = sy + sh * .5 + ih * 1.3;
            double top = top0 + (top1 - top0) * e, left = sx + (sw - iw) / 2;
            g.drawImage(famCur, left, top, iw, ih);
            g.setFont(Font.font("Tahoma", Math.max(11, Math.min(16, sw / 80))));
            g.setTextAlign(TextAlignment.CENTER); g.setFill(Color.rgb(153, 153, 153));
            g.fillText("(แตะเพื่อปิดภาพ)", sx + sw / 2, top + ih + 22);
        }

        // แฟลช
        if (flashAt > 0 && now - flashAt < 250) {
            g.setFill(Color.rgb(255, 255, 255, 230 * (1 - (now - flashAt) / 250.0) / 255.0));
            g.fillRect(sx - 20, sy - 20, sw + 40, sh + 40);
        }

        g.restore();

        g.setFont(Font.font("Tahoma", 12)); g.setFill(Color.rgb(119, 119, 119));
        g.setTextAlign(TextAlignment.LEFT);
        g.fillText("คลิกวัตถุในฉาก · กด H เพื่อดูพื้นที่คลิก", sx + 12, sy + 20);
    }

    // ================== เสียงสังเคราะห์ ==================
    static class Sound {
        static final int SR = 44100;

        static float[] mix(float[]... parts) {
            int n = 0; for (float[] p : parts) n = Math.max(n, p.length);
            float[] o = new float[n];
            for (float[] p : parts) for (int i = 0; i < p.length; i++) o[i] += p[i];
            return o;
        }
        static float[] shift(float[] a, double t0) {
            int k = (int) (t0 * SR); float[] o = new float[a.length + k];
            System.arraycopy(a, 0, o, k, a.length); return o;
        }

        /** ฟิลเตอร์ biquad (RBJ) : lp / hp / bp */
        static float[] filter(float[] x, String type, double f, double q) {
            double w = 2 * Math.PI * f / SR, cs = Math.cos(w), al = Math.sin(w) / (2 * q);
            double b0, b1, b2, a0 = 1 + al, a1 = -2 * cs, a2 = 1 - al;
            switch (type) {
                case "lp": b0 = (1 - cs) / 2; b1 = 1 - cs; b2 = b0; break;
                case "hp": b0 = (1 + cs) / 2; b1 = -(1 + cs); b2 = b0; break;
                default:   b0 = al; b1 = 0; b2 = -al;
            }
            double x1 = 0, x2 = 0, y1 = 0, y2 = 0; float[] y = new float[x.length];
            for (int i = 0; i < x.length; i++) {
                double o = (b0 * x[i] + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0;
                x2 = x1; x1 = x[i]; y2 = y1; y1 = o; y[i] = (float) o;
            }
            return y;
        }

        static float[] noise(double d, double gain, String type, double f, double t0) {
            int n = (int) (SR * d); float[] a = new float[n];
            for (int i = 0; i < n; i++) a[i] = (float) ((RND.nextDouble() * 2 - 1) * Math.pow(1 - i / (double) n, 2));
            a = filter(a, type, f, type.equals("bp") ? 1.0 : 0.7071);
            for (int i = 0; i < n; i++) a[i] *= gain;
            return shift(a, t0);
        }

        /** เสียงเซรามิกกระทบ: "แก๊ง" ครั้งแรก แล้วเศษกระทบพื้นเป็นเสียงกริ๊งสั้นๆ ต่อเนื่อง */
        static float[] ping(double f, double t0, double g, double dur) {
            int n = (int) (SR * dur); float[] a = new float[n]; double ph = 0;
            for (int i = 0; i < n; i++) {
                double t = i / (double) SR; ph += 2 * Math.PI * f / SR;
                a[i] = (float) ((Math.sin(ph) + .5 * Math.sin(ph * 2.76)) * Math.exp(-t * 6 / dur) * g
                        + (i < 60 ? (RND.nextDouble() * 2 - 1) * g : 0));
            }
            return shift(a, t0);
        }

        static float[] plate() {
            List<float[]> p = new ArrayList<>();
            p.add(noise(.10, 1.0, "hp", 2500, 0));            // เสียงแตกแรก
            p.add(noise(.30, .6, "hp", 4000, .02));
            p.add(noise(.25, .35, "bp", 3000, .10));
            p.add(ping(2400, 0, .35, .30));
            for (int i = 0; i < 18; i++)                        // เศษกระทบพื้น
                p.add(ping(1500 + RND.nextDouble() * 4500, .12 + Math.pow(RND.nextDouble(), 1.6) * .95,
                           .12 + RND.nextDouble() * .22, .06 + RND.nextDouble() * .12));
            for (int i = 0; i < 3; i++)                         // ชิ้นใหญ่กระแทกโต๊ะ
                p.add(noise(.12, .5, "lp", 900, .15 + RND.nextDouble() * .4));
            return mix(p.toArray(new float[0][]));
        }

        static float[] scream() {
            int n = SR; float[] o = new float[n]; double ph = 0;
            for (int i = 0; i < n; i++) {
                double t = i / (double) SR, f;
                if (t < .25) f = 600 * Math.pow(1500 / 600.0, t / .25); else f = 1500 + (1100 - 1500) * Math.min(1, (t - .25) / .65);
                f += 60 * Math.sin(2 * Math.PI * 9 * t);
                ph += f / SR;
                double saw = 2 * (ph - Math.floor(ph)) - 1, gn;
                if (t < .08) gn = .0001 * Math.pow(.22 / .0001, t / .08);
                else if (t < .8) gn = .22;
                else gn = .22 * Math.pow(.0001 / .22, Math.min(1, (t - .8) / .15));
                o[i] = (float) (saw * gn);
            }
            return filter(o, "bp", 1500, .5);
        }

        static float[] sweep(double f0, double f1, double dur, double g0, double gDur, double total) {
            int n = (int) (SR * total); float[] o = new float[n]; double ph = 0;
            for (int i = 0; i < n; i++) {
                double t = i / (double) SR, f = f0 * Math.pow(f1 / f0, Math.min(1, t / dur));
                ph += 2 * Math.PI * f / SR;
                double gn = g0 * Math.pow(.001 / g0, Math.min(1, t / gDur));
                o[i] = (float) (Math.sin(ph) * gn);
            }
            return o;
        }

        static float[] shot() { return mix(noise(.5, 1.4, "lp", 2500, 0), sweep(140, 30, .3, 1, .35, .4)); }
        static float[] thud() { return mix(sweep(90, 25, .5, 1.2, .6, .7), noise(.4, .6, "lp", 400, 0)); }

        static void play(float[] data) {
            try {
                byte[] b = new byte[data.length * 2];
                for (int i = 0; i < data.length; i++) {
                    double v = Math.tanh(data[i]);
                    short sv = (short) (v * 32767);
                    b[2 * i] = (byte) (sv & 0xff); b[2 * i + 1] = (byte) ((sv >> 8) & 0xff);
                }
                AudioFormat fmt = new AudioFormat(SR, 16, 1, true, false);
                Clip c = AudioSystem.getClip(); c.open(fmt, b, 0, b.length);
                c.addLineListener(ev -> { if (ev.getType() == LineEvent.Type.STOP) c.close(); });
                c.start();
            } catch (Exception ignored) { }
        }

        static void playFile(String path) {
            try {
                Clip c = AudioSystem.getClip();
                c.open(AudioSystem.getAudioInputStream(new File(path)));
                c.addLineListener(ev -> { if (ev.getType() == LineEvent.Type.STOP) c.close(); });
                c.start();
            } catch (Exception ignored) { }
        }
    }
}
