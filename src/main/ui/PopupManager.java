package ui;

import audio.AudioDataStructure;
import audio.AudioFileLoader;
import model.AudioConversion;
import model.ExceptionIgnore;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

import static java.awt.GridBagConstraints.*;
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
        ERROR(0),
        WARNING(1);

        public final int iconIndex;

        ErrorImageTypes(int iconIndex) {
            this.iconIndex = iconIndex;
        }
    }

    // Gets images for popups
    static {
        BufferedImage[] myImages;
        ClassLoader classLoader = Main.class.getClassLoader();
        try {
            myImages = new BufferedImage[]{
                    ImageIO.read(classLoader.getResourceAsStream("data/FolderIcon.png")),
                    ImageIO.read(classLoader.getResourceAsStream("data/FileIcon.png")),
                    ImageIO.read(classLoader.getResourceAsStream("data/FileIcon_RAW.png")),
                    ImageIO.read(classLoader.getResourceAsStream("data/FileIcon_AAC.png")),
                    ImageIO.read(classLoader.getResourceAsStream("data/FileIcon_MPEG.png")),
                    ImageIO.read(classLoader.getResourceAsStream("data/FileIcon_Vorbis.png")),
                    ImageIO.read(classLoader.getResourceAsStream("data/FileIcon_ALAC.png")),
                    ImageIO.read(classLoader.getResourceAsStream("data/FileIcon_FLAC.png"))
            };
        } catch (Exception e) {
            try {
                System.out.println("Not running in JAR");
                myImages = new BufferedImage[]{
                        ImageIO.read(new File("data/spec/FolderIcon.png")),
                        ImageIO.read(new File("data/spec/FileIcon.png")),
                        ImageIO.read(new File("data/spec/FileIcon_RAW.png")),
                        ImageIO.read(new File("data/spec/FileIcon_AAC.png")),
                        ImageIO.read(new File("data/spec/FileIcon_MPEG.png")),
                        ImageIO.read(new File("data/spec/FileIcon_Vorbis.png")),
                        ImageIO.read(new File("data/spec/FileIcon_ALAC.png")),
                        ImageIO.read(new File("./data/spec/FileIcon_FLAC.png"))
                };
            } catch (Exception ex) {
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
        }
        FILE_BROWSER_IMAGES = myImages;
        try {
            myImages = new BufferedImage[]{
                    ImageIO.read(classLoader.getResourceAsStream("data/ErrorIcon.png")),
                    ImageIO.read(classLoader.getResourceAsStream("data/WarningIcon.png"))
            };
        } catch (Exception e) {
            try {
                myImages = new BufferedImage[]{
                        ImageIO.read(new File("./data/spec/ErrorIcon.png")),
                        ImageIO.read(new File("./data/spec/WarningIcon.png"))
                };
            } catch (Exception ex) {
                myImages = new BufferedImage[]{
                        new BufferedImage(32, 32, BufferedImage.TYPE_3BYTE_BGR),
                        new BufferedImage(32, 32, BufferedImage.TYPE_3BYTE_BGR)
                };
            }
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
        private boolean forceDirCheck = false;

        { // Initialize open commands
            file.addActionListener(e -> {
                forceDirCheck = false;
                doFileSelection(new File(location
                        + separatorChar + e.getActionCommand()));
            });
            openButton.addActionListener(e -> {
                forceDirCheck = true;
                doFileSelection(new File(location
                        + separatorChar + file.getText()));
            });
        }

        private JFrame selector;
        private boolean allowOut = false;
        private final JTable fileTable = new JTable(new FileTableModel());
        private final JScrollPane fileList = new JScrollPane(fileTable);
        private File[] dirList;
        private PopupResponder responder;
        private Boolean hasNoParent;

        // Setup file table events
        {
            fileTable.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent evt) {
                    int row = fileTable.rowAtPoint(evt.getPoint());
                    int col = fileTable.columnAtPoint(evt.getPoint());
                    if (row >= 0 && col >= 0) {
                        forceDirCheck = false;
                        if (hasNoParent) {
                            row++;
                        }
                        if (evt.getButton() == 1) {
                            if (evt.getClickCount() == 2) {
                                if (row == 0) {
                                    doFileSelection(new File(location).getParentFile());
                                } else {
                                    doFileSelection(dirList[row - 1]);
                                }
                            } else if (evt.getClickCount() == 1) {
                                ExceptionIgnore.ignoreExc(() ->
                                        file.setText((String) getFileData(dirList[fileTable.rowAtPoint(evt.getPoint())
                                                - (hasNoParent ? 0 : 1)], "Filename")));
                            }
                        } else if (evt.getButton() == 3) {
                            System.out.println("Right click!");
                        }
                    }
                }
            });
        }

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
                e.printStackTrace();
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
                case "Icon": // Thanks IntelliJ!
                    return new ImageIcon(FILE_BROWSER_IMAGES[file.isDirectory() ? 0 :
                            AudioFileLoader.getAudioFiletype(file.getAbsolutePath()).iconIndex + 1]);
                default:
                    return String.valueOf(file.length());
            }
        }

        // Effects: does file selection
        private void doFileSelection(File file) {
            if (file.isDirectory() && !forceDirCheck) {
                location = file.getPath();
                this.file.setText("");
                setupFiles();
            } else if (strArrContains(filetypes, (String) getFileData(file, "Filetype"))) {
                selector.setVisible(false);
                responder.run(this);
            } else {
                new ErrorPopupFrame("Cannot select this type of file.", ErrorImageTypes.WARNING,
                        temp -> { });
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

    // public class for error popups
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
            this.errorText = new JLabel(String.format("<html>%s</html>", errorText));
            errorImg = new JLabel(new ImageIcon(ERROR_IMAGES[image.iconIndex]));
            try {
                SwingUtilities.invokeLater(() -> setup());
            } catch (Exception e) {
                e.printStackTrace();
            }
            this.responder = responder;
        }

        // Effects: sets up window objects
        private void setupWindowObjects() {
            selector.add(okButton);
            selector.add(errorImg);
            selector.add(errorText);
            errorText.setBorder(new EmptyBorder(4,4,4,4));
            errorImg.setBorder(new EmptyBorder(4,4,4,4));
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
            selector.setResizable(false);
            setupWindowObjects();
            setupWindowLayout();
            selector.pack();
            selector.setAlwaysOnTop(true);
            selector.setSize(200, 150);
            selector.setVisible(true);
        }
    }

    // public class for error popups
    public static class ConfirmationPopupFrame implements Popup {
        private JButton cancelButton = new JButton("Cancel");
        private JButton okButton = new JButton("Ok");
        private JLabel errorText;
        private JLabel errorImg;
        private JFrame selector;
        private PopupResponder responder;
        private Boolean yes = false;

        { // Initialize open commands
            cancelButton.addActionListener(e -> {
                selector.setVisible(false);
                responder.run(this);
            });
            okButton.addActionListener(e -> {
                yes = true;
                selector.setVisible(false);
                responder.run(this);
            });
        }

        @Override
        public Object getValue() {
            return yes;
        }

        // Effects: defaults everything
        public ConfirmationPopupFrame(String errorText, ErrorImageTypes image, PopupResponder responder) {
            this.errorText = new JLabel(String.format("<html>%s</html>", errorText));
            errorImg = new JLabel(new ImageIcon(ERROR_IMAGES[image.iconIndex]));
            try {
                SwingUtilities.invokeLater(() -> setup());
            } catch (Exception e) {
                e.printStackTrace();
            }
            this.responder = responder;
        }

        // Effects: sets up window objects
        private void setupWindowObjects() {
            selector.add(cancelButton);
            selector.add(okButton);
            selector.add(errorImg);
            selector.add(errorText);
            errorText.setBorder(new EmptyBorder(4,4,4,4));
            errorImg.setBorder(new EmptyBorder(4,4,4,4));
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
            layout.setConstraints(cancelButton, constraints);
            constraints.gridx = 2;
            layout.setConstraints(okButton, constraints);
            constraints.gridx = 1;
            constraints.weighty = 1;
            constraints.gridy = 0;
            constraints.fill = BOTH;
            constraints.gridwidth = 3;
            layout.setConstraints(errorText, constraints);
            selector.setLayout(layout);
        }

        // Effects: does the work
        private void setup() {
            selector = new JFrame("Confirmation");
            selector.setResizable(false);
            setupWindowObjects();
            setupWindowLayout();
            selector.pack();
            selector.setAlwaysOnTop(true);
            selector.setSize(200, 150);
            selector.setVisible(true);
        }
    }

    // public class for audio conversion
    public static class ConversionPopupFrame implements Popup {
        private JButton startButton = new JButton("Start!");
        private JFrame selector;
        private PopupResponder responder;
        private AudioConversion converter;
        private JPanel optionsPanel = new JPanel(true);
        private HashMap<String, JComboBox> options = new HashMap<>();


        { // Initialize open commands
            startButton.addActionListener(e -> {
                selector.setVisible(false);
                responder.run(this);
            });
        }

        @Override
        public Object getValue() {
            HashMap<String, String> selected = new HashMap<>();
            options.forEach((key, value) -> {
                selected.put(key, (String) value.getSelectedItem());
            });
            converter.setAudioSettings(selected);
            return converter;
        }

        // Effects: defaults everything
        public ConversionPopupFrame(AudioDataStructure source, PopupResponder responder) {
            getFileToWrite(source);
            this.responder = responder;
        }

        // Effects: sets up window objects
        private void setupWindowObjects() {
            selector.add(optionsPanel);
            selector.add(startButton);
            if (options.size() == 0) {
                optionsPanel.add(new JLabel("Encoder has no options."));
                optionsPanel.updateUI();
            } else {
                setupOptions();
            }
            optionsPanel.setBorder(new EmptyBorder(0, 4, 0, 0));
        }

        // Effects: sets up options panel UI
        private void setupOptions() {
            GridBagLayout optionsLayout = new GridBagLayout();
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridy = 0;
            constraints.fill = HORIZONTAL;
            options.forEach((key, value) -> {
                constraints.weightx = 0;
                constraints.gridx = 0;
                JLabel keyLabel = new JLabel(key);
                optionsPanel.add(keyLabel);
                optionsLayout.setConstraints(keyLabel, constraints);
                constraints.weightx = 1;
                constraints.gridx = 1;
                optionsPanel.add(value);
                optionsLayout.setConstraints(value, constraints);
                value.setSelectedIndex(value.getItemCount() - 1);
                constraints.gridy++;
            });
            optionsPanel.setLayout(optionsLayout);
            optionsPanel.updateUI();
        }

        // Effects: sets up window layout
        private void setupWindowLayout() {
            selector.setLayout(new GridBagLayout());
        }

        // Effects: does the work
        private void setup() {
            selector = new JFrame("Converter");
            selector.setResizable(false);
            selector.setSize(200, 150);
            ExceptionIgnore.ignoreExc(() -> converter.getOptions().forEach((key, value) ->
                    options.put(key, new JComboBox<>(value.toArray()))));
            setupWindowObjects();
            setupWindowLayout();
            selector.pack();
            selector.setAlwaysOnTop(true);
            selector.setVisible(true);
        }

        // Effects: gets file to write to
        private void getFileToWrite(AudioDataStructure source) {
            FilePopupFrame filePopupFrame = new FilePopupFrame(System.getProperty("user.home"), null, popup -> {
                converter = new AudioConversion(source, (String) popup.getValue());
                if (converter.errorOccurred() || new File((String) popup.getValue()).exists()) {
                    new ErrorPopupFrame("Cannot use file.", ErrorImageTypes.ERROR, t -> getFileToWrite(source));
                } else {
                    try {
                        SwingUtilities.invokeLater(() -> setup());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }
}
