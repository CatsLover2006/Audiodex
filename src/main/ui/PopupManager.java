package ui;

import audio.*;
import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.nodes.SVG;
import com.github.weisj.jsvg.parser.SVGLoader;
import model.AudioConversion;
import model.ExceptionIgnore;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

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

    private static final JVectorIcon[] FILE_BROWSER_IMAGES;
    private static final JVectorIcon[] ERROR_IMAGES;

    public enum ErrorImageTypes {
        INFO(4),
        ERROR(0),
        WARNING(1),
        HALT(3),
        CRITICAL(3);

        public final int iconIndex;

        ErrorImageTypes(int iconIndex) {
            this.iconIndex = iconIndex;
        }
    }

    private static LinkedList<Popup> popupList = new LinkedList<>();

    // Effects: updates component tree of all popups
    public static void updatePopupTrees() {
        clearPopupList();
        for (Popup popup : popupList) {
            popup.updateUI();
        }
    }

    // Effects: removes empty popups from popup list
    public static void clearPopupList() {
        for (int i = popupList.size() - 1; i >= 0; i--) {
            if (popupList.get(i).finished()) {
                popupList.remove(i);
            }
        }
    }

    private static SVGLoader loader = new SVGLoader();

    // Effects: loads image from data directory (/data/spec or (jar)/data)
    private static SVGDocument loadVector(String filename) throws IOException {
        try {
            SVGDocument t = loader.load(Main.class.getResource("/data/" + filename));
            if (t == null) {
                throw new IOException("Unable to load file");
            }
            return t;
        } catch (Exception ex) {
            try {
                return loader.load(new File("data/spec/" + filename).toURL());
            } catch (Exception e) {
                throw new IOException("Couldn't load vector: " + e.getMessage() + " and " + ex.getMessage());
            }
        }
    }

    // Gets images for popups
    static {
        JVectorIcon[] icons;
        try {
            icons = new JVectorIcon[]{
                    new JVectorIcon(loadVector("folder.svg"), 20, 20),
                    new JVectorIcon(loadVector("file.svg"), 20, 20),
                    new JVectorIcon(loadVector("file-audio.svg"), 20, 20),
                    new JVectorIcon(loadVector("folder-arrow-up.svg"), 20, 20)
            };
        } catch (IOException e) {
            icons = new JVectorIcon[]{
                    new JVectorIcon(new SVGDocument(new SVG()), 16, 16),
                    new JVectorIcon(new SVGDocument(new SVG()), 16, 16),
                    new JVectorIcon(new SVGDocument(new SVG()), 16, 16),
                    new JVectorIcon(new SVGDocument(new SVG()), 16, 16)
            };
        }
        FILE_BROWSER_IMAGES = icons;
        try {
            icons = new JVectorIcon[]{
                    new JVectorIcon(loadVector("circle-xmark.svg"), 32, 32),
                    new JVectorIcon(loadVector("triangle-exclamation.svg"), 32, 32),
                    new JVectorIcon(loadVector("diamond-exclamation.svg"), 32, 32),
                    new JVectorIcon(loadVector("hexagon-exclamation.svg"), 32, 32),
                    new JVectorIcon(loadVector("circle-information.svg"), 32, 32)
            };
        } catch (IOException e) {
            icons = new JVectorIcon[]{
                    new JVectorIcon(new SVGDocument(new SVG()), 32, 32),
                    new JVectorIcon(new SVGDocument(new SVG()), 32, 32),
                    new JVectorIcon(new SVGDocument(new SVG()), 32, 32),
                    new JVectorIcon(new SVGDocument(new SVG()), 32, 32),
                    new JVectorIcon(new SVGDocument(new SVG()), 32, 32)
            };
        }
        ERROR_IMAGES = icons;
    }

    // Make a lambda for this
    public interface PopupResponder {
        void run(Popup popup);
    }

    public interface Popup {
        Object getValue();

        // Effects: external update UI function
        void updateUI();

        boolean finished();
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

        // Effects: external update UI function
        @Override
        public void updateUI() {
            SwingUtilities.updateComponentTreeUI(selector);
        }

        // Setup file table events
        {
            fileTable.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent evt) {
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
            fileTable.getColumnModel().getColumn(0).setMaxWidth(20);
            fileTable.getColumnModel().getColumn(0).setMinWidth(20);
            fileTable.setRowHeight(20);
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
                if (columnIndex == 0) {
                    return Icon.class;
                }
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
                if (rowIndex == 0 && columnIndex == 0) {
                    return FILE_BROWSER_IMAGES[3];
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
                    if (file.isDirectory() && file.getName().lastIndexOf('.') == -1) {
                        return ".folder";
                    }
                    if (file.getName().lastIndexOf('.') == -1) {
                        return "file";
                    }
                    return file.getName().substring(file.getName().lastIndexOf('.') + 1);
                case "Icon": // Thanks IntelliJ!
                    return FILE_BROWSER_IMAGES[file.isDirectory() ? 0 :
                            AudioFileLoader.getAudioFiletype(file.getAbsolutePath()) == AudioFileType.EMPTY ? 1 : 2];
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
                new ErrorPopupFrame("Cannot select this<br>type of file.", ErrorImageTypes.WARNING,
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
            popupList.add(this);
        }

        // Effects: gets filename, returns null if it's not selected
        public String getFile() {
            return !allowOut || selector.isVisible() ? null : location + separatorChar + file.getText();
        }

        @Override
        public boolean finished() {
            return allowOut;
        }
    }

    // public class for error popups
    public static class ErrorPopupFrame implements Popup {
        private JButton okButton = new JButton("Ok");
        private JLabel errorText;
        private JLabel errorImg;
        private JFrame selector;
        private PopupResponder responder;

        @Override
        public boolean finished() {
            return !selector.isVisible();
        }

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
            errorImg = new JLabel(ERROR_IMAGES[image.iconIndex]);
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
            errorText.setBorder(new EmptyBorder(4,4,4,8));
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

        // Effects: external update UI function
        @Override
        public void updateUI() {
            SwingUtilities.updateComponentTreeUI(selector);
        }

        // Effects: does the work
        private void setup() {
            selector = new JFrame("Error");
            setupWindowObjects();
            setupWindowLayout();
            selector.setAlwaysOnTop(true);
            selector.pack();
            selector.setResizable(false);
            selector.setVisible(true);
            popupList.add(this);
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

        { // Initialize open commands
            cancelButton.addActionListener(e -> {
                selector.setVisible(false);
            });
            okButton.addActionListener(e -> {
                selector.setVisible(false);
                responder.run(this);
            });
        }

        @Override
        public Object getValue() {
            return true; // Leftover
        }

        // Effects: defaults everything
        public ConfirmationPopupFrame(String errorText, ErrorImageTypes image, PopupResponder responder) {
            this.errorText = new JLabel(String.format("<html>%s</html>", errorText));
            errorImg = new JLabel(ERROR_IMAGES[image.iconIndex]);
            try {
                SwingUtilities.invokeLater(() -> setup());
            } catch (Exception e) {
                e.printStackTrace();
            }
            this.responder = responder;
        }

        // Effects: external update UI function
        @Override
        public void updateUI() {
            SwingUtilities.updateComponentTreeUI(selector);
        }

        // Effects: sets up window objects
        private void setupWindowObjects() {
            selector.add(cancelButton);
            selector.add(okButton);
            selector.add(errorImg);
            selector.add(errorText);
            errorText.setBorder(new EmptyBorder(4,4,4,8));
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
            setupWindowObjects();
            setupWindowLayout();
            selector.setAlwaysOnTop(true);
            selector.pack();
            selector.setResizable(false);
            selector.setVisible(true);
            popupList.add(this);
        }

        @Override
        public boolean finished() {
            return !selector.isVisible();
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

        // Effects: external update UI function
        @Override
        public void updateUI() {
            SwingUtilities.updateComponentTreeUI(selector);
        }


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
            ExceptionIgnore.ignoreExc(() -> converter.getOptions().forEach((key, value) ->
                    options.put(key, new JComboBox<>(value.toArray()))));
            setupWindowObjects();
            setupWindowLayout();
            selector.pack();
            selector.setAlwaysOnTop(true);
            selector.setVisible(true);
            popupList.add(this);
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

        @Override
        public boolean finished() {
            return !selector.isVisible();
        }
    }

    // public class for audio conversion
    public static class ID3PopupFrame implements Popup {
        private JFrame editor;
        private AudioDataStructure file;
        private AudioDecoder fileScanner;
        private PopupResponder responder;
        private boolean hasEdited;
        private JLabel musicArt = new JLabel();
        private JLabel filenameLabel = new JLabel();
        private JPanel valuesPanel = new JPanel(true);
        private JScrollPane scrollPane;
        private JButton cancelButton = new JButton("Cancel");
        private JButton okButton = new JButton("Ok");

        // Effects: external update UI function
        @Override
        public void updateUI() {
            SwingUtilities.updateComponentTreeUI(editor);
        }

        @Override
        public boolean finished() {
            return !editor.isVisible();
        }

        { // Initialize open commands
            cancelButton.addActionListener(e -> {
                editor.setVisible(false);
            });
            okButton.addActionListener(e -> {
                editor.setVisible(false);
                responder.run(this);
            });
        }

        // data container class
        // using a few public fields would make this so much easier
        private static class DataClass {
            public DataClass(String key, String value) {
                dataKey = key;
                dataValue = value;
            }

            private String dataKey;
            private String dataValue;

            public String getDataKey() {
                return dataKey;
            }

            public String getDataValue() {
                return dataValue;
            }
        }

        private static final List<DataClass> dataList = new ArrayList<>();

        // Set up value list
        static {
            dataList.add(new DataClass("Title", "Title"));
            dataList.add(new DataClass("Title (sorting order)", "Title-Sort"));
            dataList.add(new DataClass("Artist", "Artist"));
            dataList.add(new DataClass("Artist (sorting order)", "Artist-Sort"));
            dataList.add(new DataClass("Album", "Album"));
            dataList.add(new DataClass("Album (sorting order)", "Album-Sort"));
            dataList.add(new DataClass("Album Artist", "AlbumArtist"));
            dataList.add(new DataClass("Album Artist (sorting order)", "AlbumArtist-Sort"));
            dataList.add(new DataClass("Arranger", "Arranger"));
            dataList.add(new DataClass("Arranger (sorting order)", "Arranger-Sort"));
            dataList.add(new DataClass("Composer", "Composer"));
            dataList.add(new DataClass("Producer", "Producer"));
            dataList.add(new DataClass("Genre", "GenreString"));
            dataList.add(new DataClass("Track Number", "Track"));
            dataList.add(new DataClass("Total Tracks", "Tracks"));
            dataList.add(new DataClass("Disc Number", "Disc"));
            dataList.add(new DataClass("Total Discs", "Discs"));
            dataList.add(new DataClass("Comment", "Comment"));
        }

        private static final JVectorIcon MUSIC_ICON;

        // Load music icon
        static {
            JVectorIcon temp;
            try {
                temp = new JVectorIcon(loadVector("music.svg"), 48, 48);
            } catch (IOException e) {
                temp = new JVectorIcon(new SVGDocument(new SVG()), 48, 48);
            }
            MUSIC_ICON = temp;
        }

        // Thread to update album artwork
        private class AlbumArtworkUpdater extends Thread {
            @Override
            public void run() {
                Thread.currentThread().setPriority(2);
                ExceptionIgnore.ignoreExc(() -> {
                    musicArt.setIcon(MUSIC_ICON);
                    BufferedImage bufferedImage = (BufferedImage) fileScanner.getArtwork().getImage();
                    if (bufferedImage != null) {
                        int newWidth = (int)(Math.min(48.0 / bufferedImage.getWidth(), 48.0 / bufferedImage.getHeight())
                                * bufferedImage.getWidth());
                        int newHeight = (int)(Math.min(48.0 / bufferedImage.getWidth(),
                                48.0 / bufferedImage.getHeight()) * bufferedImage.getHeight());
                        BufferedImage resized = new BufferedImage(newWidth, newHeight, bufferedImage.getType());
                        Graphics2D g = resized.createGraphics();
                        setGraphicsHints(g);
                        g.drawImage(bufferedImage, 0, 0, newWidth, newHeight, 0, 0, bufferedImage.getWidth(),
                                bufferedImage.getHeight(), null);
                        g.dispose();
                        musicArt.setIcon(new ImageIcon(resized));
                        musicArt.setPreferredSize(new Dimension(newWidth, newHeight));
                    }
                });
            }

            // Effects: Sets graphics hints
            private void setGraphicsHints(Graphics2D g) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                        RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            }
        }

        // Effects:  returns updated ID3 data
        @Override
        public Object getValue() {
            ID3Container nu = new ID3Container();
            for (JTextFieldKey field : fields) {
                nu.setID3Long(field.getKey(), field.getText());
            }
            return nu;
        }

        // Effects: defaults everything
        public ID3PopupFrame(AudioDataStructure file, PopupResponder responder) {
            this.file = file;
            fileScanner = AudioFileLoader.loadFile(file.getFilename());
            this.responder = responder;
            hasEdited = false;
            try {
                SwingUtilities.invokeLater(() -> setup());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Effects: sets up window objects
        private void setupWindowObjects() {
            setupValuePane();
            scrollPane = new JScrollPane();
            scrollPane.setViewportView(valuesPanel);
            scrollPane.setVerticalScrollBar(scrollPane.createVerticalScrollBar());
            scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            editor.add(musicArt);
            editor.add(filenameLabel);
            editor.add(okButton);
            editor.add(cancelButton);
            filenameLabel.setText(getID3Value("Title"));
            filenameLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
            filenameLabel.setBorder(new EmptyBorder(0, 4, 0, 4));
            editor.add(scrollPane);
            scrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
            new AlbumArtworkUpdater().start();
        }

        // Virtual class for JTextField which stores its key (makes life easier)
        private static class JTextFieldKey extends JEditorPane {
            private String key;

            // Modifies: this
            // Effects:  sets key
            public void setKey(String nuKey) {
                key = nuKey;
            }

            // Effects: gets key
            public String getKey() {
                return key;
            }
        }

        private List<JTextFieldKey> fields = new ArrayList<>();

        // Effects: sets up value pane (objects and layout)
        private void setupValuePane() {
            GridBagLayout valuesLayout = new GridBagLayout();
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridy = 0;
            constraints.fill = HORIZONTAL;
            for (DataClass data : dataList) {
                constraints.weightx = 0;
                constraints.gridx = 0;
                JLabel keyLabel = new JLabel(data.getDataKey());
                valuesPanel.add(keyLabel);
                valuesLayout.setConstraints(keyLabel, constraints);
                constraints.weightx = 1;
                constraints.gridx = 1;
                JTextFieldKey field = new JTextFieldKey();
                valuesPanel.add(field);
                fields.add(field);
                field.setKey(data.getDataValue());
                field.setText(getID3Value(data.getDataValue()));
                valuesLayout.setConstraints(field, constraints);
                constraints.gridy++;
            }
            valuesPanel.setLayout(valuesLayout);
        }

        // Effects: gets text for value
        private String getID3Value(String key) {
            try {
                return file.getId3Data().getID3Data(key).toString();
            } catch (NullPointerException e) {
                return "";
            }
        }

        // Effects: sets up window layout
        private void setupWindowLayout() {
            GridBagLayout layout = new GridBagLayout();
            layout.setConstraints(filenameLabel, new GridBagConstraints(1, 0, 3, 1, 1, 0, CENTER, HORIZONTAL,
                    new Insets(0, 0, 0, 0), 0, 0));
            layout.setConstraints(okButton, new GridBagConstraints(2, 2, 2, 1, 1, 0, CENTER, HORIZONTAL,
                    new Insets(0, 0, 0, 0), 0, 0));
            layout.setConstraints(cancelButton, new GridBagConstraints(0, 2, 2, 1, 1, 0, CENTER, HORIZONTAL,
                    new Insets(0, 0, 0, 0), 0, 0));
            layout.setConstraints(musicArt, new GridBagConstraints(0, 0, 1, 1, 0, 0, CENTER, NONE,
                    new Insets(0, 0, 0, 0), 0, 0));
            layout.setConstraints(scrollPane, new GridBagConstraints(0, 1, 4, 1, 1, 1, CENTER, BOTH,
                    new Insets(0, 0, 0, 0), 0, 0));
            editor.setLayout(layout);
        }

        // Effects: does the work
        private void setup() {
            editor = new JFrame(file.getFilename());
            editor.setResizable(true);
            editor.setSize(400, 400);
            setupWindowObjects();
            setupWindowLayout();
            editor.setAlwaysOnTop(false);
            editor.setVisible(true);
            popupList.add(this);
        }
    }
}
