package ui;

import audio.AudioDataStructure;
import audio.AudioDecoder;
import audio.AudioFileLoader;
import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.nodes.SVG;
import org.jaudiotagger.tag.images.ArtworkFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static java.awt.GridBagConstraints.CENTER;
import static java.awt.GridBagConstraints.HORIZONTAL;
import static ui.PopupManager.loadVector;

// Artwork updater frame
public class ArtworkUpdaterFrame extends JFrame {
    AudioDataStructure audioStructure;
    SettingsFrame.Responder[] responder;
    AudioDecoder decoder;
    JLabel art;
    
    // Responder interface for lambdas
    public interface Responder {
        void run();
    }
    
    // Attaches the settings frames
    public ArtworkUpdaterFrame(AudioDataStructure structure, SettingsFrame.Responder[] responder) {
        audioStructure = structure;
        decoder = AudioFileLoader.loadFile(structure.getFilename());
        if (!decoder.isReady()) {
            dispose();
        }
        this.responder = responder;
        GridBagLayout layout = new GridBagLayout();
        JPanel temp = new JPanel();
        JLabel title = new JLabel("Album Artwork");
        title.putClientProperty("FlatLaf.styleClass", "h3.regular");
        add(title);
        layout.setConstraints(title, new GridBagConstraints(0, 0, 2, 1, 1, 0,
                CENTER, HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
        art = new JLabel();
        updateArt();
        add(art);
        layout.setConstraints(art, new GridBagConstraints(0, 1, 2, 1, 1, 1,
                CENTER, HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
        JButton remove = new JButton("Remove Artwork");
        remove.addActionListener(e -> {
            decoder.removeArtwork();
            updateArt();
        });
        add(remove);
        layout.setConstraints(remove, new GridBagConstraints(0, 2, 1, 1, 1, 0,
                CENTER, HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
        JButton replace = new JButton("Replace Artwork");
        replace.addActionListener(e -> {
            new PopupManager.FilePopupFrame(App.fileSystemView.getDefaultDirectory().getAbsolutePath(), null,
                    popup -> {
                        File file = new File((String) popup.getValue());
                        try {
                            decoder.setArtwork(ArtworkFactory.createArtworkFromFile(file));
                            updateArt();
                        } catch (Exception ex) {
                            new PopupManager.ErrorPopupFrame("Invalid image.",
                                    PopupManager.ErrorImageTypes.ERROR, null);
                        }
                    });
        });
        add(replace);
        layout.setConstraints(replace, new GridBagConstraints(1, 2, 1, 1, 1, 0,
                CENTER, HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
        setLayout(layout);
        setResizable(false);
        pack();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setVisible(true);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                decoder.closeAudioFile();
            }
        });
    }
    
    private void updateArt() {
        try {
            art.setIcon(new ImageIcon(new GenerativeResolutionImage(cloneImage((Image) decoder.getArtwork().getImage()),
                    320, 320)));
        } catch (Exception e) {
            try {
                art.setIcon(new JVectorIcon(loadVector("music.svg"), 320, 320));
            } catch (IOException ex) {
                art.setIcon(new JVectorIcon(new SVGDocument(new SVG()), 320, 320));
            }
        }
        art.revalidate();
        art.repaint();
    }
    
    private BufferedImage cloneImage(Image image) {
        BufferedImage out =
                new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics graphics = out.getGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return out;
    }
}
