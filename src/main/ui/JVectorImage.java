package ui;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.attributes.ViewBox;

import java.awt.*;
import java.awt.image.AbstractMultiResolutionImage;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

// Vector Image class; unused but useful for future
public class JVectorImage extends AbstractMultiResolutionImage {
    private SVGDocument svg;
    private int width;
    private int height;
    
    // Effects: "registers" the SVGDocument
    public JVectorImage(SVGDocument document, int w, int h) {
        svg = document;
        width = w;
        height = h;
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
    
    /**
     * Return the base image representing the best version of the image for
     * rendering at the default width and height.
     *
     * @return the base image of the set of multi-resolution images
     * @since 9
     */
    @Override
    protected Image getBaseImage() {
        return getResolutionVariant(width, height);
    }
}
