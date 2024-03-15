package ui;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.attributes.ViewBox;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class JVectorIcon implements Icon {
    private SVGDocument svg;
    private int width;
    private int height;

    public JVectorIcon(SVGDocument document, int w, int h) {
        svg = document;
        width = w;
        height = h;
    }

    @Override
    public void paintIcon(Component c, Graphics graphics, int x, int y) {
        BufferedImage render = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Color fgColor = c.getForeground();
        Graphics2D draw = render.createGraphics();
        draw.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        draw.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        svg.render(null, draw, new ViewBox(width, height));
        draw.dispose();
        for (int workX = 0; workX < width; workX++) {
            for (int workY = 0; workY < height; workY++) {
                Color pixel = new Color(render.getRGB(workX, workY), true);
                int rgba = (pixel.getAlpha() << 24) | (fgColor.getRed() << 16)
                        | (fgColor.getGreen() << 8) | fgColor.getBlue();
                render.setRGB(workX, workY, rgba);
            }
        }
        graphics.drawImage(render, (c.getWidth() - width) / 2, (c.getHeight() - height) / 2, null);
        graphics.dispose();
    }

    @Override
    public int getIconWidth() {
        return width;
    }

    @Override
    public int getIconHeight() {
        return height;
    }
}
