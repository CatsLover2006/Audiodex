package ui;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.attributes.ViewBox;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

// Vector Image class; unused but useful for future
public class JVectorImage extends BufferedImage {
    
    // Effects: "registers" the SVGDocument
    public JVectorImage(SVGDocument document, int w, int h) {
        super(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D draw = this.createGraphics();
        draw.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        draw.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        document.render(null, draw, new ViewBox(w, h));
        draw.dispose();
    }
}
