package ui;

import audio.*;
import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.nodes.SVG;
import com.github.weisj.jsvg.parser.SVGLoader;
import model.AudioConversion;
import model.ExceptionIgnore;
import model.FileManager;
import org.apache.commons.lang3.SystemUtils;
import oshi.SystemInfo;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HWPartition;
import oshi.hardware.UsbDevice;
import oshi.software.os.OSFileStore;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static java.awt.GridBagConstraints.*;
import static java.io.File.separatorChar;

// Static class
public class PopupManager {
    private static FileSystemView fileSystemView = FileSystemView.getFileSystemView();

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
        CRITICAL(2);

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
                    new JVectorIcon(loadVector("folder-arrow-up.svg"), 20, 20),
                    new JVectorIcon(loadVector("file-block.svg"), 20, 20),
                    new JVectorIcon(loadVector("folder-block.svg"), 20, 20),
                    new JVectorIcon(loadVector("folder-user.svg"), 20, 20),
                    new JVectorIcon(loadVector("folder-shield.svg"), 20, 20),
                    new JVectorIcon(loadVector("floppy-disk.svg"), 20, 20),
                    new JVectorIcon(loadVector("disc-alt.svg"), 20, 20),
                    new JVectorIcon(loadVector("hard-drive.svg"), 20, 20),
                    new JVectorIcon(loadVector("usb-flash-drive.svg"), 20, 20),
                    new JVectorIcon(loadVector("folder-arrow-right.svg"), 20, 20),
                    new JVectorIcon(loadVector("file-arrow-right.svg"), 20, 20)
            };
        } catch (IOException e) {
            icons = new JVectorIcon[]{
                    new JVectorIcon(new SVGDocument(new SVG()), 20, 20),
                    new JVectorIcon(new SVGDocument(new SVG()), 20, 20),
                    new JVectorIcon(new SVGDocument(new SVG()), 20, 20),
                    new JVectorIcon(new SVGDocument(new SVG()), 20, 20),
                    new JVectorIcon(new SVGDocument(new SVG()), 20, 20),
                    new JVectorIcon(new SVGDocument(new SVG()), 20, 20),
                    new JVectorIcon(new SVGDocument(new SVG()), 20, 20),
                    new JVectorIcon(new SVGDocument(new SVG()), 20, 20),
                    new JVectorIcon(new SVGDocument(new SVG()), 20, 20),
                    new JVectorIcon(new SVGDocument(new SVG()), 20, 20),
                    new JVectorIcon(new SVGDocument(new SVG()), 20, 20),
                    new JVectorIcon(new SVGDocument(new SVG()), 20, 20),
                    new JVectorIcon(new SVGDocument(new SVG()), 20, 20)
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

    // popup interface (lambda responder)
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
        private JLabel folderLook;
        private File[] dirList;
        private PopupResponder responder;
        private Boolean inWindowsDriveList = false;

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
                        if (!hasParent(new File(location))) {
                            row++;
                        }
                        if (evt.getButton() == 1) {
                            if (evt.getClickCount() == 2) {
                                if (row == 0) {
                                    doParentSelection();
                                } else {
                                    doFileSelection(dirList[row - 1]);
                                }
                            } else if (evt.getClickCount() == 1) {
                                ExceptionIgnore.ignoreExc(() ->
                                        file.setText((String) getFileData(dirList[fileTable.rowAtPoint(evt.getPoint())
                                                - (hasParent(new File(location)) ? 1 : 0)], "Filename")));
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
            folderLook = new JLabel(getFile());
            try {
                SwingUtilities.invokeLater(() -> setup());
            } catch (Exception e) {
                ExceptionIgnore.logException(e);
            }
            this.responder = responder;
        }

        // Effects: sets up window objects
        private void setupWindowObjects() {
            setupFiles();
            selector.add(fileList);
            selector.add(file);
            selector.add(folderLook);
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
            folderLook.setText(getFile());
            dirList = listFiles();
            fileTable.getColumnModel().getColumn(0).setMaxWidth(20);
            fileTable.getColumnModel().getColumn(0).setMinWidth(20);
            fileTable.setRowHeight(20);
            FileManager.updateRootStores();
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
                return dirList.length + (hasParent(new File(location)) ? 1 : 0);
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
                if (!hasParent(new File(location))) {
                    rowIndex++;
                }
                if (rowIndex == 0 && columnIndex == 0) {
                    return FILE_BROWSER_IMAGES[3];
                }
                if (rowIndex == 0 && columnIndex == 1) {
                    return "..";
                }
                if (rowIndex == 0 && new File(location).getParentFile() == null) {
                    return "";
                }
                if (rowIndex == 0) {
                    return getFileData(new File(location).getParentFile(), columns[columnIndex]);
                }
                return getFileData(dirList[rowIndex - 1], columns[columnIndex]);
            }
        }

        // Effects: Returns true if this file has a parent
        private boolean hasParent(File file) {
            if (SystemUtils.IS_OS_WINDOWS) {
                return !inWindowsDriveList;
            }
            return file.getParentFile() != null;
        }

        // Effects: lists files in a directory (creates special menu for windows drive selection)
        private File[] listFiles() {
            if (SystemUtils.IS_OS_WINDOWS && inWindowsDriveList) {
                return File.listRoots();
            }
            return new File(location).listFiles(pathname -> !pathname.isHidden());
        }

        // Effects: gets the file data for the file list
        private Object getFileData(File file, String type) {
            switch (type) {
                case "Filename":
                    if (inWindowsDriveList) {
                        return file.getPath();
                    }
                    return file.getName();
                case "Last Modified":
                    return new Date(file.lastModified()).toString();
                case "Filetype":
                    return getFileExt(file);
                case "Icon":
                    return FILE_BROWSER_IMAGES[getIcon(file)];
                default:
                    return String.valueOf(file.length());
            }
        }

        // Effects: gets file extension
        private String getFileExt(File file) {
            if (!file.exists()) {
                return ".empty";
            }
            if (file.isDirectory()) {
                return ".folder";
            }
            if (getFileExtensionLoc(file.getPath()) == -1) {
                return "file";
            }
            return file.getName().substring(getFileExtensionLoc(file.getName()) + 1);
        }

        // Effects: gets index of image for file browser
        private int getIcon(File file) {
            if (file.isDirectory()) {
                if (FileManager.isRoot(file) || inWindowsDriveList) {
                    return getDeviceType(file);
                }
                if (!fileSystemView.isTraversable(file)) {
                    return 5;
                }
                if (new File(System.getProperty("user.home")).getAbsolutePath().equals(file.getAbsolutePath())) {
                    return 6;
                }
                File[] dirlist = file.listFiles((dir, name) -> name.equals("index.audiodex.db"));
                if (dirlist != null && dirlist.length != 0) {
                    return 7;
                }
                return checkSymlink(file);
            }
            if (!file.canRead()) {
                return 4;
            }
            if (AudioFileLoader.getAudioFiletype(file.getAbsolutePath()) == AudioFileType.EMPTY) {
                return checkSymlink(file);
            }
            return 2;
        }

        // Effects: gets index of image of device for file browser
        private static int getDeviceType(File file) {
            SystemInfo sysInfo = new SystemInfo();
            List<OSFileStore> fileStores = sysInfo.getOperatingSystem().getFileSystem().getFileStores();
            List<HWDiskStore> diskStores = sysInfo.getHardware().getDiskStores();
            if (SystemUtils.IS_OS_WINDOWS) {
                return doWindowsDiskCheck(file, sysInfo, fileStores, diskStores);
            }
            return doUnixDiskCheck(file, sysInfo, fileStores, diskStores);
        }

        // Effects: getDeviceType extension for Windows
        private static int doWindowsDiskCheck(File file, SystemInfo sysInfo, List<OSFileStore> fileStores,
                                              List<HWDiskStore> diskStores) {
            for (OSFileStore store : fileStores) {
                if (file.getAbsolutePath().equals(store.getMount())) {
                    for (HWDiskStore diskStore : diskStores) {
                        if (isUSB(diskStore, sysInfo.getHardware().getUsbDevices(true))) {
                            for (HWPartition partition : diskStore.getPartitions()) {
                                if (store.getUUID().equalsIgnoreCase(partition.getUuid())) {
                                    return 11;
                                }
                            }
                        }
                    }
                    return getDiskStoreType(store);
                }
            }
            return 10;
        }

        // Effects: getDeviceType extension for macOS (and maybe Linux)
        private static int doUnixDiskCheck(File file, SystemInfo sysInfo, List<OSFileStore> fileStores,
                                           List<HWDiskStore> diskStores) {
            for (HWDiskStore diskStore : diskStores) {
                for (HWPartition partition : diskStore.getPartitions()) {
                    if (file.getAbsolutePath().equals(partition.getMountPoint())) {
                        if (isUSB(diskStore, sysInfo.getHardware().getUsbDevices(true))) {
                            return 11;
                        }
                        if (diskStore.getSerial().isEmpty()) {
                            return 9;
                        }
                        System.out.println();
                    }
                }
            }
            return 10;
        }

        // Effects: returns the icon ID for a device description
        //          (being as OS-independent as possible here)
        private static int getDiskStoreType(OSFileStore store) {
            switch (store.getDescription().toLowerCase()) {
                case "cd-rom":
                    return 9;
                case "local disk":
                case "fixed drive":
                default:
                    return 10;
            }
        }

        // Effect: checks if a file/folder is a symlink
        private static int checkSymlink(File file) {
            int out = 0;
            try {
                out = file.getAbsolutePath().equals(file.getCanonicalPath()) ? 0 : 12;
            } catch (IOException e) {
                ExceptionIgnore.logException(e);
            }
            if (file.isFile()) {
                out++;
            }
            return out;
        }

        // Effects: returns true if diskStore is a USB device
        //          this function is recursive
        private static boolean isUSB(HWDiskStore diskStore, List<UsbDevice> devices) {
            for (UsbDevice device : devices) {
                if (diskStore.getModel().toLowerCase().contains(device.getName().toLowerCase())
                        || device.getName().toLowerCase().contains(diskStore.getModel().toLowerCase())) {
                    return true;
                }
                if (isUSB(diskStore, device.getConnectedDevices())) {
                    return true;
                }
            }
            return false;
        }

        // Effects: returns file extension of a file
        private static int getFileExtensionLoc(String path) {
            return path.lastIndexOf('.');
        }

        // Effects: does PARENT selection (windows drive detection)
        private void doParentSelection() {
            if (SystemUtils.IS_OS_WINDOWS && new File(location).getParentFile() == null) {
                inWindowsDriveList = true;
                this.file.setText("");
                setupFiles();
                return;
            }
            doFileSelection(new File(location).getParentFile());
        }

        // Effects: does file selection
        private void doFileSelection(File file) {
            if (!file.canRead() && !strArrContains(filetypes, ".empty")) {
                return;
            }
            if (file.isDirectory() && (!forceDirCheck || inWindowsDriveList)) {
                try {
                    location = file.getCanonicalPath();
                } catch (IOException e) {
                    location = file.getAbsolutePath();
                }
                inWindowsDriveList = false;
                this.file.setText("");
                setupFiles();
            } else if (strArrContains(filetypes, (String) getFileData(file, "Filetype")) && !inWindowsDriveList) {
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
            constraints.gridwidth = 2;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            layout.setConstraints(folderLook, constraints);
            constraints.fill = GridBagConstraints.NONE;
            constraints.gridwidth = 1;
            constraints.gridy = 2;
            layout.setConstraints(openButton, constraints);
            constraints.weightx = 1.0;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            layout.setConstraints(file, constraints);
            constraints.weighty = 1.0;
            constraints.gridwidth = 2;
            constraints.gridy = 1;
            constraints.fill = BOTH;
            layout.setConstraints(fileList, constraints);
            selector.setLayout(layout);
        }

        // Effects: does the work
        private void setup() {
            selector = new JFrame("File Selector Window");
            selector.setIconImage(App.getAppImage());
            selector.setSize(300, 300);
            selector.setResizable(true);
            setupWindowObjects();
            setupWindowLayout();
            System.out.println("Done objects...");
            selector.pack();
            selector.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            selector.setAlwaysOnTop(true);
            selector.setVisible(true);
            allowOut = true;
            popupList.add(this);
        }

        // Effects: gets filename, returns directory if it's not selected
        public String getFile() {
            if (inWindowsDriveList) {
                return "Drive selection";
            }
            return !allowOut || selector.isVisible() ? location : location + separatorChar + file.getText();
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
                ExceptionIgnore.logException(e);
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
            selector.setIconImage(App.getAppImage());
            setupWindowObjects();
            setupWindowLayout();
            selector.setAlwaysOnTop(true);
            selector.pack();
            selector.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            selector.setResizable(false);
            selector.revalidate();
            selector.getContentPane().repaint();
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
        private PopupResponder noResponder;

        { // Initialize open commands
            cancelButton.addActionListener(e -> {
                selector.setVisible(false);
                if (noResponder == null) {
                    return;
                }
                noResponder.run(this);
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
                ExceptionIgnore.logException(e);
            }
            this.responder = responder;
        }

        // Effects: defaults everything
        public ConfirmationPopupFrame(String errorText, ErrorImageTypes image, PopupResponder responder,
                                      PopupResponder noResponder) {
            this.errorText = new JLabel(String.format("<html>%s</html>", errorText));
            errorImg = new JLabel(ERROR_IMAGES[image.iconIndex]);
            try {
                SwingUtilities.invokeLater(() -> setup());
            } catch (Exception e) {
                ExceptionIgnore.logException(e);
            }
            this.responder = responder;
            this.noResponder = noResponder;
        }

        // Effects: defaults everything
        public ConfirmationPopupFrame(String errorText, ErrorImageTypes image, PopupResponder responder,
                                      String confirmText, String denyText) {
            this.errorText = new JLabel(String.format("<html>%s</html>", errorText));
            errorImg = new JLabel(ERROR_IMAGES[image.iconIndex]);
            okButton.setText(confirmText);
            cancelButton.setText(denyText);
            try {
                SwingUtilities.invokeLater(() -> setup());
            } catch (Exception e) {
                ExceptionIgnore.logException(e);
            }
            this.responder = responder;
        }

        // Effects: defaults everything
        public ConfirmationPopupFrame(String errorText, ErrorImageTypes image, PopupResponder responder,
                                      PopupResponder noResponder, String confirmText, String denyText) {
            this.errorText = new JLabel(String.format("<html>%s</html>", errorText));
            errorImg = new JLabel(ERROR_IMAGES[image.iconIndex]);
            okButton.setText(confirmText);
            cancelButton.setText(denyText);
            try {
                SwingUtilities.invokeLater(() -> setup());
            } catch (Exception e) {
                ExceptionIgnore.logException(e);
            }
            this.responder = responder;
            this.noResponder = noResponder;
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
            constraints.gridy = 1;
            constraints.fill = HORIZONTAL;
            constraints.gridwidth = 2;
            constraints.weightx = 1;
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
            selector.setIconImage(App.getAppImage());
            setupWindowObjects();
            setupWindowLayout();
            selector.setAlwaysOnTop(true);
            selector.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            selector.pack();
            selector.setResizable(false);
            selector.revalidate();
            selector.getContentPane().repaint();
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
            selector.setIconImage(App.getAppImage());
            selector.setResizable(false);
            ExceptionIgnore.ignoreExc(() -> converter.getOptions().forEach((key, value) ->
                    options.put(key, new JComboBox<>(value.toArray()))));
            setupWindowObjects();
            setupWindowLayout();
            selector.pack();
            selector.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            selector.setAlwaysOnTop(true);
            selector.setVisible(true);
            popupList.add(this);
        }

        // Effects: gets file to write to
        private void getFileToWrite(AudioDataStructure source) {
            FilePopupFrame filePopupFrame = new FilePopupFrame(fileSystemView.getDefaultDirectory().getAbsolutePath(),
                    new String[]{".empty"}, popup -> {
                converter = new AudioConversion(source, (String) popup.getValue());
                if (converter.errorOccurred() || new File((String) popup.getValue()).exists()) {
                    new ErrorPopupFrame("Cannot use file.", ErrorImageTypes.ERROR, t -> getFileToWrite(source));
                } else {
                    try {
                        SwingUtilities.invokeLater(() -> setup());
                    } catch (Exception e) {
                        ExceptionIgnore.logException(e);
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
                        double newWidth = Math.min(48.0 / bufferedImage.getWidth(),
                                48.0 / bufferedImage.getHeight()) * bufferedImage.getWidth();
                        double newHeight = Math.min(48.0 / bufferedImage.getWidth(),
                                48.0 / bufferedImage.getHeight()) * bufferedImage.getHeight();
                        Image quickSize = bufferedImage.getScaledInstance((int) newWidth, (int) newHeight,
                                Image.SCALE_AREA_AVERAGING);
                        musicArt.setIcon(new ImageIcon(quickSize));
                        BaseMultiResolutionImage conv = new BaseMultiResolutionImage(quickSize,
                                bufferedImage.getScaledInstance((int) (newWidth * 1.5), (int) (newHeight * 1.5),
                                        Image.SCALE_AREA_AVERAGING),
                                bufferedImage.getScaledInstance((int) (newWidth * 2), (int) (newHeight * 2),
                                        Image.SCALE_AREA_AVERAGING),
                                bufferedImage.getScaledInstance((int) (newWidth * 2.5), (int) (newHeight * 2.5),
                                        Image.SCALE_AREA_AVERAGING),
                                bufferedImage.getScaledInstance((int) (newWidth * 3), (int) (newHeight * 3),
                                        Image.SCALE_AREA_AVERAGING));
                        musicArt.setIcon(new ImageIcon(conv));
                        musicArt.setPreferredSize(new Dimension((int) newWidth, (int) newHeight));
                    }
                });
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
                ExceptionIgnore.logException(e);
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
            editor.setIconImage(App.getAppImage());
            editor.setResizable(true);
            editor.setSize(400, 400);
            setupWindowObjects();
            setupWindowLayout();
            editor.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            editor.setAlwaysOnTop(false);
            editor.setVisible(true);
            popupList.add(this);
        }
    }
}
