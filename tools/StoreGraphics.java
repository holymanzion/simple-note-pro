import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Draws the Play Store icon (512x512) and feature graphic (1024x500) from the app's own
 * launcher-icon vector, so the store art always matches the installed icon.
 *
 *   java tools/StoreGraphics.java
 *
 * Writes to store-assets/. Supports the path commands the icon uses (M, L, Q, Z).
 */
public class StoreGraphics {
    static final Color AMBER = new Color(0xF4, 0xB4, 0x00);
    static final Color INK = new Color(0x2B, 0x20, 0x08);

    record Shape(Color fill, Path2D path) {}

    public static void main(String[] args) throws Exception {
        String xml = Files.readString(Path.of("app/src/main/res/drawable/ic_launcher_foreground.xml"));
        List<Shape> shapes = parse(xml);
        new File("store-assets").mkdirs();
        ImageIO.write(icon(shapes, 512), "png", new File("store-assets/icon-512.png"));
        ImageIO.write(feature(shapes), "png", new File("store-assets/feature-graphic.png"));
        System.out.println("Wrote store-assets/icon-512.png and store-assets/feature-graphic.png");
    }

    /** Reads each <path fillColor=… pathData=…> from the 108x108 vector. */
    static List<Shape> parse(String xml) {
        List<Shape> shapes = new ArrayList<>();
        Matcher m = Pattern.compile("<path\\s+([^>]*?)/>", Pattern.DOTALL).matcher(xml);
        while (m.find()) {
            String attrs = m.group(1);
            String fill = attr(attrs, "fillColor");
            String data = attr(attrs, "pathData");
            shapes.add(new Shape(color(fill), path(data)));
        }
        return shapes;
    }

    static String attr(String attrs, String name) {
        Matcher m = Pattern.compile("android:" + name + "=\"([^\"]*)\"").matcher(attrs);
        if (!m.find()) throw new IllegalStateException("missing " + name);
        return m.group(1);
    }

    static Color color(String hex) {
        String h = hex.substring(1);
        if (h.length() == 6) return new Color(Integer.parseInt(h, 16));
        long v = Long.parseLong(h, 16); // AARRGGBB
        return new Color((int) (v >> 16 & 0xFF), (int) (v >> 8 & 0xFF), (int) (v & 0xFF), (int) (v >> 24 & 0xFF));
    }

    static Path2D path(String data) {
        Path2D.Double p = new Path2D.Double();
        Matcher t = Pattern.compile("[MLQZmlqz]|-?\\d*\\.?\\d+").matcher(data);
        List<String> tokens = new ArrayList<>();
        while (t.find()) tokens.add(t.group());
        int i = 0;
        char cmd = 'M';
        while (i < tokens.size()) {
            String tok = tokens.get(i);
            if (Character.isLetter(tok.charAt(0))) { cmd = tok.charAt(0); i++; }
            switch (Character.toUpperCase(cmd)) {
                case 'M' -> { p.moveTo(num(tokens, i), num(tokens, i + 1)); i += 2; cmd = 'L'; }
                case 'L' -> { p.lineTo(num(tokens, i), num(tokens, i + 1)); i += 2; }
                case 'Q' -> { p.quadTo(num(tokens, i), num(tokens, i + 1), num(tokens, i + 2), num(tokens, i + 3)); i += 4; }
                case 'Z' -> p.closePath();
                default -> throw new IllegalStateException("unsupported path command " + cmd);
            }
        }
        return p;
    }

    static double num(List<String> tokens, int i) { return Double.parseDouble(tokens.get(i)); }

    static Graphics2D smooth(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    /** Full-bleed square, as Play requires: it applies its own rounded mask. */
    static BufferedImage icon(List<Shape> shapes, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = smooth(img);
        g.setColor(AMBER);
        g.fillRect(0, 0, size, size);
        g.scale(size / 108.0, size / 108.0);
        for (Shape s : shapes) { g.setColor(s.fill()); g.fill(s.path()); }
        g.dispose();
        return img;
    }

    static BufferedImage feature(List<Shape> shapes) {
        int w = 1024, h = 500;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = smooth(img);
        g.setPaint(new GradientPaint(0, 0, new Color(0xFF, 0xD2, 0x5E), w, h, new Color(0xF0, 0xA2, 0x00)));
        g.fillRect(0, 0, w, h);
        // Soft sheets in the background for texture.
        g.setColor(new Color(255, 255, 255, 46));
        g.fill(new RoundRectangle2D.Double(700, -40, 260, 240, 72, 72));
        g.fill(new RoundRectangle2D.Double(820, 260, 260, 280, 72, 72));

        // Icon in the rounded-square shape launchers use, with a soft shadow.
        int iconSize = 260, x = 90, y = (h - iconSize) / 2;
        g.setColor(new Color(60, 40, 0, 40));
        g.fill(new RoundRectangle2D.Double(x, y + 10, iconSize, iconSize, 128, 128));
        RoundRectangle2D.Double clip = new RoundRectangle2D.Double(x, y, iconSize, iconSize, 128, 128);
        g.setClip(clip);
        g.drawImage(icon(shapes, iconSize), x, y, null);
        g.setClip(null);
        g.setColor(new Color(255, 255, 255, 90));
        g.setStroke(new BasicStroke(2f));
        g.draw(clip);

        int tx = x + iconSize + 56;
        g.setColor(INK);
        g.setFont(font(Font.BOLD, 66));
        g.drawString("Simple Note Pro", tx, 218);
        g.setColor(new Color(0x4A, 0x37, 0x08));
        g.setFont(font(Font.PLAIN, 34));
        g.drawString("Notes, checklists, reminders", tx, 282);
        g.drawString("and photos. Private by design.", tx, 328);
        g.dispose();
        return img;
    }

    /** A clean sans-serif that every Windows machine has, with a portable fallback. */
    static Font font(int style, int size) {
        for (String name : new String[] {"Segoe UI", "Roboto", "Arial"}) {
            Font f = new Font(name, style, size);
            if (f.getFamily().equals(name)) return f;
        }
        return new Font(Font.SANS_SERIF, style, size);
    }
}
