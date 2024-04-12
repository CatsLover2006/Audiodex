package ui;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.attributes.ViewBox;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

// Colorable Vector Image class
public class JVectorImageColorable extends BufferedImage {
    private SVGDocument svg;
    private int width;
    private int height;
    private Color color;
    
    // Effects: "registers" the SVGDocument
    public JVectorImageColorable(SVGDocument document, int w, int h) {
        super(w, h, BufferedImage.TYPE_INT_ARGB);
        svg = document;
        color = new Color(0);
        width = w;
        height = h;
    }
    
    // Effects: sets the color to render
    public void setColor(Color color) {
        this.color = color;
        Graphics2D draw = this.createGraphics();
        draw.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        draw.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        svg.render(null, draw, new ViewBox(width, height));
        draw.dispose();
        for (int workX = 0; workX < width; workX++) {
            for (int workY = 0; workY < height; workY++) {
                Color pixel = new Color(this.getRGB(workX, workY), true);
                int rgba = (pixel.getAlpha() << 24) | (color.getRed() << 16)
                        | (color.getGreen() << 8) | color.getBlue();
                this.setRGB(workX, workY, rgba);
            }
        }
    }
}
