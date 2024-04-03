package ui;

import model.ApplicationSettings;
import model.ExceptionIgnore;

import javax.swing.*;
import java.awt.*;

import static java.awt.GridBagConstraints.CENTER;
import static java.awt.GridBagConstraints.HORIZONTAL;

// Settings frame class
public class SettingsFrame extends JFrame {
    ApplicationSettings settings;
    Responder[] responder;

    // Responder interface for lambdas
    public interface Responder {
        void run();
    }

    // Attaches the settings frames
    public SettingsFrame(ApplicationSettings settings, Responder[] responder) {
        this.settings = settings;
        this.responder = responder;
        setSettings();
        GridBagLayout layout = new GridBagLayout();
        JPanel temp = new JPanel();
        JLabel title = new JLabel("Settings");
        title.putClientProperty("FlatLaf.styleClass", "h3.regular");
        temp.add(title);
        layout.setConstraints(title, new GridBagConstraints(0, 0, 1, 1, 1, 0,
                CENTER, HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
        for (int i = 0; i < checkBoxes.length; i++) {
            temp.add(checkBoxes[i]);
            layout.setConstraints(checkBoxes[i], new GridBagConstraints(0, i + 1, 1, 1, 1, 0,
                    CENTER, HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
        }
        temp.setLayout(layout);
        temp.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        setResizable(false);
        add(temp);
        pack();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setVisible(true);
    }

    // Sets the settings checkboxes
    private void setSettings() {
        checkBoxes[0].setSelected(settings.doSoundCheck());
    }

    JCheckBox[] checkBoxes = new JCheckBox[] {
            new JCheckBox("ReplayGain")
    };

    // Set up update listeners
    {
        checkBoxes[0].addChangeListener(e -> {
            if (checkBoxes[0].isSelected() != settings.doSoundCheck()) {
                settings.toggleSoundCheck();
            }
            ExceptionIgnore.ignoreExc(() -> responder[0].run());
        });
    }
}
