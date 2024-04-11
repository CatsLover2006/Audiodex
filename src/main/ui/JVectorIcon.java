package ui;

import com.github.weisj.jsvg.SVGDocument;

import javax.swing.*;
import java.awt.*;

// Vector Icon class
public class JVectorIcon extends ImageIcon {
    private JVectorImageColorable image;
    private int height;
    private int width;
    

    // Effects: "registers" the SVGDocument
    public JVectorIcon(SVGDocument document, int w, int h) {
        image = new JVectorImageColorable(document, w, h);
        super.setImage(image);
        width = w;
        height = h;
    }

    // Effects: Paints icon (in foreground color)
    @Override
    public void paintIcon(Component c, Graphics graphics, int x, int y) {
        image.setColor(c.getForeground());
        super.paintIcon(c, graphics, (c.getWidth() - width) / 2, (c.getHeight() - height) / 2);
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
