package ui;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.attributes.ViewBox;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;

import static java.util.Arrays.asList;

// Vector Icon class
public class JVectorIcon extends ImageIcon {
    private SVGDocument svg;
    private int width;
    private int height;
    
    // Effects: gets window scale
    public static double getWindowScale(Window window) {
        GraphicsDevice device = getWindowDevice(window);
        return device.getDisplayMode().getWidth() / (double) device.getDefaultConfiguration().getBounds().width;
    }
    
    // Effects: gets graphics device attached to this window
    public static GraphicsDevice getWindowDevice(Window window) {
        Rectangle bounds = window.getBounds();
        return asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()).stream()
                // pick devices where window located
                .filter(d -> d.getDefaultConfiguration().getBounds().intersects(bounds))
                // sort by biggest intersection square
                .sorted((f, s) -> Long.compare(//
                        square(f.getDefaultConfiguration().getBounds().intersection(bounds)),
                        square(s.getDefaultConfiguration().getBounds().intersection(bounds))))
                // use one with the biggest part of the window
                .reduce((f, s) -> s) //
                // fallback to default device
                .orElse(window.getGraphicsConfiguration().getDevice());
    }
    
    // Effects: returns area of Rectangle
    public static long square(Rectangle rec) {
        return Math.abs(rec.width * rec.height);
    }
    

    // Effects: "registers" the SVGDocument
    public JVectorIcon(SVGDocument document, int w, int h) {
        svg = document;
        width = w;
        height = h;
        setImage(new BaseMultiResolutionImage(renderScaledImage(new Color(0), 1.0),
                renderScaledImage(new Color(0), 2.0), renderScaledImage(new Color(0), 3.0)));
    }

    // Effects: Paints icon (in foreground color)
    @Override
    public void paintIcon(Component c, Graphics graphics, int x, int y) {
        setImage(new BaseMultiResolutionImage(renderScaledImage(c.getForeground(), 1.0),
                renderScaledImage(c.getForeground(), 2.0), renderScaledImage(c.getForeground(), 3.0)));
        super.paintIcon(c, graphics, (c.getWidth() - width) / 2, (c.getHeight() - height) / 2);
    }
    
    // Effects: renders icon at resolution
    private BufferedImage renderScaledImage(Color fgColor, double scale) {
        BufferedImage render = new BufferedImage((int)(width * scale), (int) (height * scale),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D draw = render.createGraphics();
        draw.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        draw.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        svg.render(null, draw, new ViewBox((int)(width * scale), (int) (height * scale)));
        draw.dispose();
        for (int workX = 0; workX < (int)(width * scale); workX++) {
            for (int workY = 0; workY < (int)(height * scale); workY++) {
                Color pixel = new Color(render.getRGB(workX, workY), true);
                int rgba = (pixel.getAlpha() << 24) | (fgColor.getRed() << 16)
                        | (fgColor.getGreen() << 8) | fgColor.getBlue();
                render.setRGB(workX, workY, rgba);
            }
        }
        return render;
    }

    // Effects: returns width
    @Override
    public int getIconWidth() {
        return width;
    }

    // Effects: returns width
    @Override
    public int getIconHeight() {
        return height;
    }
}
