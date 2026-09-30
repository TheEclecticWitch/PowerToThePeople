import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the app icon - the same civic building as androidApp's ic_launcher_foreground.xml, on the
 * same navy - as a square PNG for iOS and the stores. Coordinates are the vector's 108-unit grid.
 *
 * Run from the project root with any JDK 11+:
 *   java tools/icon/MakeIcon.java iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png 1024
 */
public class MakeIcon {
    public static void main(String[] args) throws Exception {
        String out = args[0];
        int size = Integer.parseInt(args[1]);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x1F3A5F));
        g.fillRect(0, 0, size, size);
        // The drawing sits in 26..82 of 108; widen it a little for a square icon with no mask.
        double scale = size / 108.0 * 1.12;
        double offset = 54 - 54 / 1.12;
        g.scale(scale, scale);
        g.translate(-offset, -offset + 1);

        Color parchment = new Color(0xF6F1E4);
        g.setColor(parchment);
        Path2D pediment = new Path2D.Double();
        pediment.moveTo(54, 30); pediment.lineTo(80, 42); pediment.lineTo(28, 42); pediment.closePath();
        g.fill(pediment);

        g.setColor(new Color(0x1F3A5F));
        double[][] star = {{54,33.6},{55.1,36.6},{58.2,36.6},{55.7,38.4},{56.6,41.3},{54,39.5},{51.4,41.3},{52.3,38.4},{49.8,36.6},{52.9,36.6}};
        Path2D s = new Path2D.Double();
        s.moveTo(star[0][0], star[0][1]);
        for (int i = 1; i < star.length; i++) s.lineTo(star[i][0], star[i][1]);
        s.closePath();
        g.fill(s);

        g.setColor(parchment);
        g.fill(new Rectangle2D.Double(29, 43.5, 50, 3));
        for (double x : new double[]{33, 44, 59, 70}) g.fill(new Rectangle2D.Double(x, 48, 5, 20));
        g.fill(new Rectangle2D.Double(29, 69.5, 50, 3));
        g.fill(new Rectangle2D.Double(26, 74, 56, 3.5));
        g.setColor(new Color(0xC9A44C));
        g.fill(new Rectangle2D.Double(26, 79, 56, 1.5));
        g.dispose();
        ImageIO.write(img, "png", new File(out));
        System.out.println("wrote " + out);
    }
}
