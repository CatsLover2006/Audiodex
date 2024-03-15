package ui;

import model.DataManager;

import javax.swing.*;
import java.awt.*;

// Database Manager window
public class DatabaseManagerFrame extends JFrame {
    private JButton revertButton = new JButton("Revert Database");
    private JButton cleanButton = new JButton("Clean Database Backups");
    private JButton refreshButton = new JButton("Refresh Database Folder");
    private DataManager database;

    // Set up button actions
    {
        revertButton.addActionListener(e ->
            new PopupManager.ConfirmationPopupFrame("Are you sure you want to<br>revert to the previous<br>"
                    + "database version?", PopupManager.ErrorImageTypes.HALT, popup -> {
                database.revertDb();
                App.Gui.updateUI();
            }));
        cleanButton.addActionListener(e ->
                new PopupManager.ConfirmationPopupFrame("Are you sure you want to<br>remove all database "
                        + "backups?<br>This will remove your<br>ability to revert to an earlier<br>database.",
                        PopupManager.ErrorImageTypes.HALT, popup -> {
                    database.cleanOldDb();
                }));
        refreshButton.addActionListener(e ->
                new PopupManager.ConfirmationPopupFrame("Are you sure you want to clear database folder?"
                        + "<br>This will remove any extra files<br>(not folders) in the database folder.",
                        PopupManager.ErrorImageTypes.CRITICAL, popup -> {
                    database.cleanOldDb();
                }));
    }

    DatabaseManagerFrame(DataManager database) {
        super("Here be dragons...");
        this.database = database;
        add(revertButton);
        add(cleanButton);
        add(refreshButton);
        setLayout(new GridLayout(2, 2));
        pack();
        setVisible(true);
    }
}
