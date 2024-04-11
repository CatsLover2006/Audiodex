package ui;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.attributes.ViewBox;

import java.awt.*;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

// Colorable Vector Image class
public class JVectorImageColorable extends BaseMultiResolutionImage {
    private SVGDocument svg;
    private int width;
    private int height;
    private Color color;
    
    // Effects: "registers" the SVGDocument
    public JVectorImageColorable(SVGDocument document, int w, int h) {
        super(getDefaultImage(document, w, h));
        svg = document;
        width = w;
        height = h;
        color = new Color(0);
    }
    
    // Effects: sets the color to render
    public void setColor(Color color) {
        this.color = color;
    }
    
    /**
     * Default Javadoc
     *
     * Gets a specific image that is the best variant to represent
     * this logical image at the indicated size.
     *
     * @param destImageWidth  the width of the destination image, in pixels.
     * @param destImageHeight the height of the destination image, in pixels.
     * @return image resolution variant.
     * @throws IllegalArgumentException if {@code destImageWidth} or
     *                                  {@code destImageHeight} is less than or equal to zero, infinity,
     *                                  or NaN.
     * @since 9
     */
    @Override
    public Image getResolutionVariant(double destImageWidth, double destImageHeight) {
        BufferedImage render = new BufferedImage((int) destImageWidth, (int) destImageHeight,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D draw = render.createGraphics();
        draw.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        draw.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        svg.render(null, draw, new ViewBox((int) destImageWidth, (int) destImageHeight));
        draw.dispose();
        for (int workX = 0; workX < (int) destImageWidth; workX++) {
            for (int workY = 0; workY < (int) destImageHeight; workY++) {
                Color pixel = new Color(render.getRGB(workX, workY), true);
                int rgba = (pixel.getAlpha() << 24) | (color.getRed() << 16)
                        | (color.getGreen() << 8) | color.getBlue();
                render.setRGB(workX, workY, rgba);
            }
        }
        return render;
    }
    
    // Effects: default render
    private static Image getDefaultImage(SVGDocument document, int w, int h) {
        BufferedImage render = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D draw = render.createGraphics();
        draw.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        draw.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        document.render(null, draw, new ViewBox(w, h));
        draw.dispose();
        return render;
    }
    
    /**
     * Default Javadoc
     *
     * Gets a readable list of all resolution variants.
     * The list must be nonempty and contain at least one resolution variant.
     * <p>
     * Note that many implementations might return an unmodifiable list.
     *
     * @return list of resolution variants.
     * @since 9
     */
    @Override
    public List<Image> getResolutionVariants() {
        List<Image> defaults = new ArrayList<>();
        defaults.add(getResolutionVariant(width, height));
        return defaults;
    }
}
