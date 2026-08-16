import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Draws the launcher icon set for the app. Run from the android/ folder:
 *
 * <pre>java android/tools/IconGen.java</pre>
 *
 * Produces the legacy mipmaps, the adaptive-icon foreground layers, the in-app
 * logo and a large PNG for docs. Committing the generator (not just the PNGs)
 * keeps the artwork editable.
 */
public final class IconGen {

    // The board: dark green studio, emerald tiles, one amber "trial tile" lit up.
    private static final Color STAGE_TOP = new Color(0x14, 0x33, 0x1E);
    private static final Color STAGE_BOTTOM = new Color(0x04, 0x10, 0x09);
    private static final Color TILE_TOP = new Color(0x2A, 0xA5, 0x5C);
    private static final Color TILE_BOTTOM = new Color(0x11, 0x5A, 0x30);
    private static final Color TILE_EDGE = new Color(0x0A, 0x2E, 0x18);
    private static final Color AMBER_TOP = new Color(0xFF, 0xD9, 0x5E);
    private static final Color AMBER_BOTTOM = new Color(0xE3, 0x8E, 0x08);
    private static final Color GLOW = new Color(0xFB, 0xBF, 0x24);

    public static void main(String[] args) throws Exception {
        File res = new File(args.length > 0 ? args[0] : "app/src/main/res");

        int[][] densities = {
                {48, 108}, // mdpi:    legacy px, adaptive px
                {72, 162}, // hdpi
                {96, 216}, // xhdpi
                {144, 324}, // xxhdpi
                {192, 432}, // xxxhdpi
        };
        String[] names = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};

        for (int i = 0; i < densities.length; i++) {
            File dir = new File(res, "mipmap-" + names[i]);
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            int legacy = densities[i][0];
            int adaptive = densities[i][1];
            write(legacyIcon(legacy, false), new File(dir, "ic_launcher.png"));
            write(legacyIcon(legacy, true), new File(dir, "ic_launcher_round.png"));
            write(adaptiveForeground(adaptive), new File(dir, "ic_launcher_foreground.png"));
        }

        File drawable = new File(res, "drawable-nodpi");
        //noinspection ResultOfMethodCallIgnored
        drawable.mkdirs();
        write(adaptiveForeground(288), new File(drawable, "ic_tile_logo.png"));
        write(legacyIcon(512, false), new File(res, "../../../../../docs/icon-512.png"));
        System.out.println("Icons written under " + res.getAbsolutePath());
    }

    /** Full icon including its own background, for pre-Oreo launchers and docs. */
    private static BufferedImage legacyIcon(int size, boolean round) {
        BufferedImage image = blank(size);
        Graphics2D g = graphics(image);
        float radius = round ? size / 2f : size * 0.22f;
        if (round) {
            g.setClip(new Ellipse2D.Float(0, 0, size, size));
        } else {
            g.setClip(new RoundRectangle2D.Float(0, 0, size, size, radius * 2, radius * 2));
        }
        paintStage(g, size);
        drawGrid(g, size, size * 0.74f);
        g.dispose();
        return image;
    }

    /**
     * Adaptive-icon foreground: transparent, artwork kept inside the middle
     * two-thirds so launchers can mask it into any shape.
     */
    private static BufferedImage adaptiveForeground(int size) {
        BufferedImage image = blank(size);
        Graphics2D g = graphics(image);
        drawGrid(g, size, size * 0.5f);
        g.dispose();
        return image;
    }

    private static void paintStage(Graphics2D g, int size) {
        g.setPaint(new GradientPaint(0, 0, STAGE_TOP, 0, size, STAGE_BOTTOM));
        g.fillRect(0, 0, size, size);
        // Spotlight from above, like a game show set.
        g.setPaint(new RadialGradientPaint(
                new Point2D.Float(size * 0.5f, size * 0.12f),
                size * 0.85f,
                new float[]{0f, 1f},
                new Color[]{new Color(0x34, 0xD3, 0x99, 70), new Color(0x34, 0xD3, 0x99, 0)}));
        g.fillRect(0, 0, size, size);
    }

    /** A 3x3 board with the centre trial tile lit up. */
    private static void drawGrid(Graphics2D g, int canvas, float gridSize) {
        float gap = gridSize * 0.075f;
        float tile = (gridSize - gap * 2f) / 3f;
        float left = (canvas - gridSize) / 2f;
        float top = (canvas - gridSize) / 2f;
        float corner = tile * 0.26f;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                boolean centre = row == 1 && col == 1;
                if (centre) {
                    continue; // drawn last so its glow sits on top
                }
                float x = left + col * (tile + gap);
                float y = top + row * (tile + gap);
                drawTile(g, x, y, tile, corner, TILE_TOP, TILE_BOTTOM, row == 0 && col == 2);
                drawClueLines(g, x, y, tile);
            }
        }

        // The lit trial tile: bigger, warmer, glowing.
        float scale = 1.16f;
        float lit = tile * scale;
        float x = left + (tile + gap) - (lit - tile) / 2f;
        float y = top + (tile + gap) - (lit - tile) / 2f;

        float glowRadius = lit * 1.15f;
        g.setPaint(new RadialGradientPaint(
                new Point2D.Float(x + lit / 2f, y + lit / 2f),
                glowRadius,
                new float[]{0f, 0.55f, 1f},
                new Color[]{new Color(GLOW.getRed(), GLOW.getGreen(), GLOW.getBlue(), 150),
                        new Color(GLOW.getRed(), GLOW.getGreen(), GLOW.getBlue(), 60),
                        new Color(GLOW.getRed(), GLOW.getGreen(), GLOW.getBlue(), 0)}));
        g.fill(new Ellipse2D.Float(x + lit / 2f - glowRadius, y + lit / 2f - glowRadius,
                glowRadius * 2, glowRadius * 2));

        drawTile(g, x, y, lit, corner * scale, AMBER_TOP, AMBER_BOTTOM, true);
        drawQuestionMark(g, x, y, lit);
    }

    /** Faint bars on the unplayed tiles, so the board reads as clues. */
    private static void drawClueLines(Graphics2D g, float x, float y, float tile) {
        g.setPaint(new Color(0xEC, 0xFD, 0xF3, 55));
        float barHeight = tile * 0.085f;
        float radius = barHeight;
        float wide = tile * 0.56f;
        float narrow = tile * 0.36f;
        g.fill(new RoundRectangle2D.Float(x + (tile - wide) / 2f, y + tile * 0.36f, wide, barHeight, radius, radius));
        g.fill(new RoundRectangle2D.Float(x + (tile - narrow) / 2f, y + tile * 0.545f, narrow, barHeight, radius, radius));
    }

    /** The lit tile is the one in play. */
    private static void drawQuestionMark(Graphics2D g, float x, float y, float tile) {
        java.awt.Font font = new java.awt.Font("DejaVu Sans", java.awt.Font.BOLD, Math.round(tile * 0.74f));
        g.setFont(font);
        java.awt.FontMetrics metrics = g.getFontMetrics();
        String glyph = "?";
        int width = metrics.stringWidth(glyph);
        float baseline = y + tile / 2f + (metrics.getAscent() - metrics.getDescent()) / 2f;
        float textX = x + (tile - width) / 2f;
        g.setPaint(new Color(0x00, 0x00, 0x00, 55));
        g.drawString(glyph, textX, baseline + tile * 0.025f);
        g.setPaint(new Color(0x3B, 0x22, 0x02));
        g.drawString(glyph, textX, baseline);
    }

    private static void drawTile(Graphics2D g, float x, float y, float size, float corner,
                                 Color top, Color bottom, boolean sheen) {
        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(x, y, size, size, corner * 2, corner * 2);

        // Drop shadow keeps the tiles readable against the dark stage.
        g.setPaint(new Color(0, 0, 0, 90));
        g.fill(new RoundRectangle2D.Float(x, y + size * 0.06f, size, size, corner * 2, corner * 2));

        g.setPaint(new GradientPaint(x, y, top, x, y + size, bottom));
        g.fill(shape);

        g.setStroke(new BasicStroke(Math.max(1f, size * 0.045f)));
        g.setPaint(TILE_EDGE);
        g.draw(shape);

        if (sheen) {
            // A soft highlight across the top edge.
            java.awt.Shape clip = g.getClip();
            g.setClip(shape);
            g.setComposite(AlphaComposite.SrcOver.derive(0.30f));
            g.setPaint(new GradientPaint(x, y, Color.WHITE, x, y + size * 0.55f, new Color(255, 255, 255, 0)));
            g.fill(new RoundRectangle2D.Float(x, y, size, size * 0.55f, corner, corner));
            g.setComposite(AlphaComposite.SrcOver);
            g.setClip(clip);
        }
    }

    private static BufferedImage blank(int size) {
        return new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    private static void write(BufferedImage image, File file) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        ImageIO.write(image, "png", file);
    }
}
