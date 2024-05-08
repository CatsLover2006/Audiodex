package ui;

import java.awt.*;
import java.awt.image.AbstractMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

public class GenerativeResolutionImage extends AbstractMultiResolutionImage {
    private int baseWidth;
    private int baseHeight;
    private int originalWidth;
    private int originalHeight;
    private double ratio;
    private BufferedImage bufferedImage;
    private Map<Integer, Image> images;
    
    private void setImage(int width, int height, Image img) {
        if (ratio > 1) { // wide
            images.put(width, img);
        } else { // tall
            images.put(height, img);
        }
    }
    
    private void makeImage(int width, int height) {
        if (ratio > 1) { // wide
            images.put(width,
                    bufferedImage.getScaledInstance(width, (int) (width / ratio), Image.SCALE_AREA_AVERAGING));
        } else { // tall
            images.put(height,
                    bufferedImage.getScaledInstance((int) (height / ratio), height, Image.SCALE_AREA_AVERAGING));
        }
    }
    
    private Image getImage(int width, int height) {
        if (ratio > 1) { // wide
            if (width > originalWidth) return bufferedImage;
            return images.get(width);
        } else { // tall
            if (height > originalHeight) return bufferedImage;
            return images.get(height);
        }
    }
    
    public GenerativeResolutionImage(BufferedImage image, int displayWidth, int displayHeight) {
        bufferedImage = image;
        baseWidth = displayWidth;
        baseHeight = displayHeight;
        originalWidth = bufferedImage.getWidth();
        originalHeight = bufferedImage.getHeight();
        ratio = (double) displayWidth / displayHeight;
        images = new HashMap<>();
        makeImage(baseWidth, baseHeight);
        setImage(originalWidth, originalHeight, bufferedImage);
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
        return getImage(baseWidth, baseHeight);
    }
    
    /**
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
        if (getImage((int) destImageWidth, (int) destImageHeight) == null) {
            makeImage((int) destImageWidth, (int) destImageHeight);
        }
        return getImage((int) destImageWidth, (int) destImageHeight);
    }
    
    /**
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
        List<Image> imageList = new ArrayList<>();
        imageList.addAll(images.values());
        return imageList;
    }
}
