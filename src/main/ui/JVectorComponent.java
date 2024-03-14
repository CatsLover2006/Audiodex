package ui;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.attributes.ViewBox;

import javax.swing.*;
import java.awt.*;

public class JVectorComponent extends JComponent {
    private SVGDocument svg;

    public JVectorComponent(SVGDocument document) {
        svg = document;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        svg.render(this, (Graphics2D) graphics, new ViewBox(getWidth(), getHeight()));
    }
}
