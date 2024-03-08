package ui;

import audio.AudioFileLoader;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.EventObject;

import static java.awt.GridBagConstraints.BOTH;
import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.io.File.separatorChar;

// Static class
public class PopupManager {

    // Effects: returns true if arr contains item, false otherwise
    //          this function is null-safe
    private static boolean strArrContains(String[] arr, String item) {
        if (arr == null) {
            return true;
        }
        return Arrays.stream(arr).anyMatch(cur -> cur.equals(item));
    }

    private static final BufferedImage[] FILE_BROWSER_IMAGES;
    private static final BufferedImage[] ERROR_IMAGES;

    public enum ErrorImageTypes {
        ERROR(0);

        public final int iconIndex;

        ErrorImageTypes(int iconIndex) {
            this.iconIndex = iconIndex;
        }
    }

    // Gets images for popups
    static {
        BufferedImage[] myImages;
        try {
            myImages = new BufferedImage[]{
                    ImageIO.read(new File("./data/spec/FolderIcon.png")),
                    ImageIO.read(new File("./data/spec/FileIcon.png")),
                    ImageIO.read(new File("./data/spec/FileIcon_RAW.png")),
                    ImageIO.read(new File("./data/spec/FileIcon_AAC.png")),
                    ImageIO.read(new File("./data/spec/FileIcon_MPEG.png")),
                    ImageIO.read(new File("./data/spec/FileIcon_Vorbis.png")),
                    ImageIO.read(new File("./data/spec/FileIcon_ALAC.png")),
                    ImageIO.read(new File("./data/spec/FileIcon_FLAC.png"))
            };
        } catch (Exception e) {
            myImages = new BufferedImage[]{
                    new BufferedImage(16, 16, BufferedImage.TYPE_3BYTE_BGR),
                    new BufferedImage(16, 16, BufferedImage.TYPE_3BYTE_BGR),
                    new BufferedImage(16, 16, BufferedImage.TYPE_3BYTE_BGR),
                    new BufferedImage(16, 16, BufferedImage.TYPE_3BYTE_BGR),
                    new BufferedImage(16, 16, BufferedImage.TYPE_3BYTE_BGR),
                    new BufferedImage(16, 16, BufferedImage.TYPE_3BYTE_BGR),
                    new BufferedImage(16, 16, BufferedImage.TYPE_3BYTE_BGR),
                    new BufferedImage(16, 16, BufferedImage.TYPE_3BYTE_BGR)
            };
        }
        FILE_BROWSER_IMAGES = myImages;
        try {
            myImages = new BufferedImage[]{
                    ImageIO.read(new File("./data/spec/ErrorIcon.png"))
            };
        } catch (Exception e) {
            myImages = new BufferedImage[]{
                    new BufferedImage(32, 32, BufferedImage.TYPE_3BYTE_BGR)
            };
        }
        ERROR_IMAGES = myImages;
    }

    // Make a lambda for this
    public interface PopupResponder {
        void run(Popup popup);
    }

    public interface Popup {
        Object getValue();
    }

    // public class for file selection popups
    public static class FilePopupFrame implements Popup {
        private String location;
        private final String[] filetypes;
        private JTextField file = new JTextField();
        private JButton openButton = new JButton("Open");

        { // Initialize open commands
            file.addActionListener(e -> doFileSelection(new File(location
                    + separatorChar + e.getActionCommand())));
            openButton.addActionListener(e -> doFileSelection(new File(location
                    + separatorChar + file.getText())));
        }

        private JFrame selector;
        private boolean allowOut = false;
        private final JTable fileTable = new JTable(new FileTableModel()) {
            @Override
            public boolean editCellAt(int row, int column, EventObject e) {
                if (hasNoParent) {
                    row++;
                }
                if (lastClicked == row) {
                    if (row == 0) {
                        doFileSelection(new File(location).getParentFile());
                    } else {
                        doFileSelection(dirList[row - 1]);
                    }
                } else {
                    lastClicked = row;
                    if (row != 0) {
                        file.setText((String) getFileData(dirList[row - 1], "Filename"));
                    }
                }
                return false;
            }
        };
        private final JScrollPane fileList = new JScrollPane(fileTable);
        private File[] dirList;
        private PopupResponder responder;
        private Boolean hasNoParent;

        @Override
        public Object getValue() {
            return getFile();
        }

        // Effects: defaults everything
        public FilePopupFrame(String defaultLoc, String[] filetypes, PopupResponder responder) {
            location = defaultLoc;
            this.filetypes = filetypes;
            file.setText("");
            try {
                SwingUtilities.invokeLater(() -> setup());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            this.responder = responder;
        }

        // Effects: sets up window objects
        private void setupWindowObjects() {
            setupFiles();
            selector.add(fileList);
            selector.add(file);
            selector.add(openButton);
        }

        private int lastClicked;
        private static final String[] columns = {
                "Icon",
                "Filename",
                "Filesize",
                "Filetype",
                "Last Modified"
        };

        // Effects: gets file list and sets up file view
        private void setupFiles() {
            File where = new File(location);
            dirList = where.listFiles(pathname -> !pathname.isHidden());
            hasNoParent = where.getParentFile() == null;
            fileTable.getColumnModel().getColumn(0).setMaxWidth(17);
            fileTable.getColumnModel().getColumn(0).setMinWidth(17);
            fileTable.setRowHeight(17);
            lastClicked = -1;
            fileList.updateUI();
        }

        // Table model to get around Jtable pain
        private class FileTableModel extends DefaultTableModel {

            // Gets table row count
            @Override
            public int getRowCount() {
                if (dirList == null) {
                    return 0;
                }
                return dirList.length + (hasNoParent ? 0 : 1);
            }

            // Gets table column count
            @Override
            public int getColumnCount() {
                return columns.length;
            }

            // Gets table column
            @Override
            public String getColumnName(int columnIndex) {
                return columns[columnIndex];
            }

            // Gets table column class
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return getValueAt(0, columnIndex).getClass();
            }

            // Prevents editing
            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return false;
            }

            // Gets table value
            @Override
            public Object getValueAt(int rowIndex, int columnIndex) {
                if (hasNoParent) {
                    rowIndex++;
                }
                if (rowIndex == 0 && columnIndex == 1) {
                    return "..";
                }
                if (rowIndex == 0) {
                    return getFileData(new File(location).getParentFile(), columns[columnIndex]);
                }
                return getFileData(dirList[rowIndex - 1], columns[columnIndex]);
            }
        }

        // Effects: gets the file data for the file list
        private static Object getFileData(File file, String type) {
            switch (type) {
                case "Filename":
                    return file.getName();
                case "Last Modified":
                    return new Date(file.lastModified()).toString();
                case "Filetype":
                    if (file.isDirectory() && file.getName().lastIndexOf(".") == -1) {
                        return ".folder";
                    } else if (file.getName().lastIndexOf(".") == -1) {
                        return "file";
                    } else {
                        return file.getName().substring(file.getName().lastIndexOf(".") + 1);
                    }
                case "Icon":
                    return new ImageIcon(file.isDirectory() ? FILE_BROWSER_IMAGES[0] : FILE_BROWSER_IMAGES[
                                    AudioFileLoader.getAudioFiletype(file.getAbsolutePath()).iconIndex + 1]);
                default:
                    return String.valueOf(file.length());
            }
        }

        // Effects: does file selection
        private void doFileSelection(File file) {
            if (file.isDirectory()) {
                location = file.getPath();
                this.file.setText("");
                setupFiles();
            } else if (strArrContains(filetypes, (String) getFileData(file, "Filetype"))) {
                selector.setVisible(false);
                responder.run(this);
            } else {
                new ErrorPopupFrame("Cannot select this type of file.", ErrorImageTypes.ERROR,
                        temp -> {
                            // Do nothing
                        });
            }
        }

        // Effects: sets up window layout
        private void setupWindowLayout() {
            GridBagLayout layout = new GridBagLayout();
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridy = 1;
            layout.setConstraints(openButton, constraints);
            constraints.weightx = 1.0;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            layout.setConstraints(file, constraints);
            constraints.weighty = 1.0;
            constraints.gridwidth = 2;
            constraints.gridy = 0;
            constraints.fill = BOTH;
            layout.setConstraints(fileList, constraints);
            selector.setLayout(layout);
        }

        // Effects: does the work
        private void setup() {
            selector = new JFrame("File Selector Window");
            selector.setSize(300, 300);
            selector.setResizable(true);
            setupWindowObjects();
            setupWindowLayout();
            System.out.println("Done objects...");
            selector.pack();
            selector.setAlwaysOnTop(true);
            selector.setVisible(true);
            allowOut = true;
            System.out.println("Done");
        }

        // Effects: gets filename, returns null if it's not selected
        public String getFile() {
            return !allowOut || selector.isVisible() ? null : location + separatorChar + file.getText();
        }
    }

    // public class for file selection popups
    public static class ErrorPopupFrame implements Popup {
        private JButton okButton = new JButton("Ok");
        private JLabel errorText;
        private JLabel errorImg;
        private JFrame selector;
        private PopupResponder responder;

        { // Initialize open commands
            okButton.addActionListener(e -> {
                selector.setVisible(false);
                responder.run(this);
            });
        }

        @Override
        public Object getValue() {
            return true;
        }

        // Effects: defaults everything
        public ErrorPopupFrame(String errorText, ErrorImageTypes image, PopupResponder responder) {
            this.errorText = new JLabel(errorText);
            errorImg = new JLabel(new ImageIcon(ERROR_IMAGES[image.iconIndex]));
            try {
                SwingUtilities.invokeLater(() -> setup());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            this.responder = responder;
        }

        // Effects: sets up window objects
        private void setupWindowObjects() {
            selector.add(okButton);
            selector.add(errorImg);
            selector.add(errorText);
        }

        // Effects: sets up window layout
        private void setupWindowLayout() {
            GridBagLayout layout = new GridBagLayout();
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = 0;
            constraints.gridy = 0;
            layout.setConstraints(errorImg, constraints);
            constraints.weightx = 1;
            constraints.gridy = 1;
            constraints.fill = HORIZONTAL;
            constraints.gridwidth = 2;
            layout.setConstraints(okButton, constraints);
            constraints.gridwidth = 1;
            constraints.weighty = 1;
            constraints.gridx = 1;
            constraints.gridy = 0;
            constraints.fill = BOTH;
            layout.setConstraints(errorText, constraints);
            selector.setLayout(layout);
        }

        // Effects: does the work
        private void setup() {
            selector = new JFrame("Error");
            selector.setSize(150, 100);
            selector.setResizable(false);
            setupWindowObjects();
            setupWindowLayout();
            selector.pack();
            selector.setAlwaysOnTop(true);
            selector.setVisible(true);
        }
    }
}
