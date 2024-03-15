package ui;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.attributes.ViewBox;

import javax.swing.*;
import java.awt.*;

// Vector component (not icon)
public class JVectorComponent extends JComponent {
    private SVGDocument svg;

    // Effects: "registers" the SVGDocument
    public JVectorComponent(SVGDocument document) {
        svg = document;
    }

    // Effects: Paints svg (in color)
    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        svg.render(this, (Graphics2D) graphics, new ViewBox(getWidth(), getHeight()));
    }
}
