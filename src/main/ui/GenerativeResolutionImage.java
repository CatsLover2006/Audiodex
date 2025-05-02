package ui;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AbstractMultiResolutionImage;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
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
    
    // This is more complex than it needs to be
    // to get around a Java bug around the ToolkitImage class
    // FUCK ToolkitImage FOR BEING USELESS AND JUST CREATING PAIN AND SUFFERING
    private void makeImage(int width, int height) {
        if (ratio > 1) { // wide
            height = (int) (width / ratio);
        } else { // tall
            width = (int) (height / ratio);
        }
        //*// Original method
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D draw = image.createGraphics();
        draw.drawImage(bufferedImage.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING), 0, 0, null);
        draw.dispose();
        image.flush();
        /*//*//*/// AffineTransform method
        int tHeight = originalHeight;
        int tWidth = originalWidth;
        BufferedImage buffer = bufferedImage;
        BufferedImage image;
        AffineTransformOp scaleOperation;
        AffineTransform scaleTransform;
        while (tWidth > width * 2 || tHeight > height * 2) {
            if (!hasImage(Math.floorDiv(tWidth, 2), Math.floorDiv(tHeight, 2))) {
                scaleTransform = // Seems pointless but should hold quality better outside powers of 2
                        AffineTransform.getScaleInstance(Math.floorDiv(tWidth, 2) / (double) tWidth,
                                Math.floorDiv(tHeight, 2) / (double) tHeight);
                scaleOperation = new AffineTransformOp(scaleTransform, AffineTransformOp.TYPE_BICUBIC);
                image = scaleOperation.filter(buffer, new BufferedImage(Math.floorDiv(tWidth, 2), Math.floorDiv(tHeight, 2), BufferedImage.TYPE_INT_ARGB));
                if (ratio > 1) { // wide
                    images.put(image.getWidth(), image);
                } else { // tall
                    images.put(image.getHeight(), image);
                }
            }
            buffer = (BufferedImage) getImage(Math.floorDiv(tWidth, 2), Math.floorDiv(tHeight, 2));
            tWidth = buffer.getWidth();
            tHeight = buffer.getHeight();
        }
        AffineTransform finalScaleTransform = AffineTransform.getScaleInstance(width / (double)tWidth, height / (double)tHeight);
        AffineTransformOp finalScaleOperation = new AffineTransformOp(finalScaleTransform, AffineTransformOp.TYPE_BICUBIC);
        image = finalScaleOperation.filter(buffer, new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB));
        /*//*//-/// Graphics2D method
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D draw = image.createGraphics();
        draw.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        draw.drawImage(bufferedImage, 0, 0, width, height, null);
        draw.dispose();
        image.flush();
        //*///
        if (ratio > 1) { // wide
            images.put(width, image);
        } else { // tall
            images.put(height, image);
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
    
    private boolean hasImage(int width, int height) {
        if (ratio > 1) { // wide
            return width > originalWidth || images.containsKey(width);
        } else { // tall
            return height > originalHeight || images.containsKey(height);
        }
    }
    
    public GenerativeResolutionImage(BufferedImage image, int displayWidth, int displayHeight) {
        bufferedImage = image;
        baseWidth = displayWidth;
        baseHeight = displayHeight;
        originalWidth = bufferedImage.getWidth();
        originalHeight = bufferedImage.getHeight();
        ratio = (double) originalWidth / originalHeight;
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
     * This method simply delegates to the same method on the base image and
     * it is equivalent to: {@code getBaseImage().getProperty(name, observer)}.
     *
     * @param name name
     * @param observer observer
     * @return the value of the named property in the base image
     * @see #getBaseImage()
     * @since 9
     */
    @Override
    public Object getProperty(String name, ImageObserver observer) {
        return bufferedImage.getProperty(name, observer);
    }
    
    /**
     * Gets a specific image that is the best variant to represent
     * this logical image at the indicated size.
     *
     * @param destImageWidth  the width of the destination image, in pixels.
     * @param destImageHeight the height of the destination image, in pixels.
     * @return image resolution variant.
     * @since 9
     */
    @Override
    public Image getResolutionVariant(double destImageWidth, double destImageHeight) {
        if (!hasImage((int) destImageWidth, (int) destImageHeight)) {
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
        List<Integer> sizeList = new ArrayList<>();
        for (Integer i : images.keySet()) {
            sizeList.add(i);
        }
        sizeList.sort(null);
        for (int size : sizeList) {
            imageList.add(images.get(size));
        }
        return imageList;
    }
}
