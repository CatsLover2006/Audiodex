package ui;

import audio.AudioDataStructure;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.List;
import java.util.logging.LogManager;

import audio.AudioDecoder;
import audio.AudioFileLoader;
import audio.ID3Container;
import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.jthemedetecor.OsThemeDetector;
import model.*;
import org.apache.commons.lang3.SystemUtils;
import org.freedesktop.dbus.DBusMap;
import org.freedesktop.dbus.DBusMatchRule;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.messages.Message;
import org.freedesktop.dbus.types.DBusMapType;
import org.freedesktop.dbus.types.Variant;
import org.fusesource.jansi.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

import org.mpris.MediaPlayer2.MediaPlayer2;
import org.mpris.MediaPlayer2.Player;
import ui.PopupManager.*;

import static java.io.File.separatorChar;
import static java.lang.Math.floor;
import static java.lang.System.exit;
import static java.lang.Thread.*;

// Main application class
public class App {
    static FileSystemView fileSystemView = FileSystemView.getFileSystemView();
    private static boolean notMain = true;
    private static OsThemeDetector detector;
    private static ID3Container id3;
    private static AudioFilePlaybackBackend playbackManager;
    private static boolean USE_CLI = false;
    private static boolean end = false;
    private static String filename = "";
    private static DataManager database;
    private static List<AudioConversion> audioConverterList;
    private static LinkedList<AudioDataStructure> played;
    private static AudioDataStructure nowPlaying;
    private static boolean loop = false;

    public static Image getAppImage() {
        return Gui.mainWindow.getIconImage();
    }

    // Effects: returns formatted time
    private static String formatTime(long seconds) {
        if (seconds == -1) {
            return "X:XX";
        }
        return String.format("%01d:%02d", seconds / 60, seconds % 60);
    }

    // Effects: Counts currently converting audio files
    private static int activeAudioConversions() {
        int i = (int) audioConverterList.stream().filter(converter -> !converter.isFinished())
                .filter(converter -> !converter.errorOccurred()).count();
        return i;
    }

    // Effects: Counts failed audio file conversions
    private static int deadAudioConversions() {
        int i = (int) audioConverterList.stream().filter(converter -> !converter.isFinished())
                .filter(AudioConversion::errorOccurred).count();
        return i;
    }


    // This is probably the lowest-level I still consider UI-level;
    // we're still not interacting with actual data on the filesystem
    // and this program basically is a file management program
    private static LinkedList<AudioDataStructure> songQueue;

    // Effects: returns true if arr contains item, false otherwise
    //          this function is null-safe
    private static boolean strArrContains(String[] arr, String item) {
        return Arrays.stream(arr).anyMatch(cur -> cur.equals(item));
    }

    // Modifies: this
    // Effects:  Is run when song is finished and redraws screen if necessary
    //           also adds previously playing song to previously played list
    public static void finishedSong() {
        if (end || notMain) {
            return;
        }
        if (USE_CLI) {
            played.addFirst(nowPlaying);
            nowPlaying = null;
            maxBoundPlayedList();
            Cli.finishedSong();
        } else {
            Gui.songFinishedPlaying();
        }
    }

    // Modifies: this
    // Effects:  Keeps previously played list to a reasonable size
    public static void maxBoundPlayedList() {
        while (played.size() > 100) {
            played.remove(played.size() - 1);
        }
    }

    // Modifies: this
    // Effects:  Is run when encode is finished and redraws screen if necessary.
    public static void finishedEncode() {
        if (end || notMain) {
            return;
        }
        for (int i = audioConverterList.size() - 1; i >= 0; i--) {
            if (audioConverterList.get(i).isFinished() && !audioConverterList.get(i).errorOccurred()) {
                audioConverterList.remove(i); // No need to update index, we're decrementing
            }
        }
        if (USE_CLI) {
            finishedEncode();
        } else {
            Gui.updateConverterView();
        }
    }

    // Modifies: this
    // Effects:  Is run when replaygain fails to run
    public static void replaygainFailure() {
        if (end || notMain) {
            return;
        }
        if (USE_CLI) {
            // Do CLI stuff
        } else {
            new PopupManager.ErrorPopupFrame("Cannot use Replaygain with currently<br>playing song on this computer."
                    + "<br>Sound will not be the correct volume.",
                    ErrorImageTypes.WARNING, obj -> { });
        }
    }


    // Effects: updates playback status in current UI
    public static void updatePlaybackStatus() {
        if (notMain) {
            return;
        }
        if (USE_CLI) {
            Cli.doPlaybackStatusWrite();
        } else {
            Gui.updatePlaybackBar();
        }
    }

    // Modifies: this
    // Effects:  is run when audio quality had to be reduced
    public static void audioQualityDegradation(AudioDataStructure qualityCheck) {
        if (end || notMain) {
            return;
        }
        if (USE_CLI) {
            // Do CLI stuff
        } else {
            if (qualityCheck == null || !qualityCheck.qualityErrorAlreadyOccured()) {
                new PopupManager.ErrorPopupFrame("Cannot play this song at full<br>quality on this system.",
                        ErrorImageTypes.WARNING, obj -> {
                });
            }
        }
        if (qualityCheck != null) {
            qualityCheck.markQualityErrorOccured();
        }
    }

    static { // Disable jaudiotagger (library) logging
        LogManager manager = LogManager.getLogManager();
        ExceptionIgnore.ignoreExc(() -> manager.readConfiguration(
                new ByteArrayInputStream(("handlers=java.util.logging.ConsoleHandler\n"
                    + "org.jaudiotagger.level=OFF").getBytes())));
    }

    // Prepares and loads data
    public static void startApp(String[] args) {
        audioConverterList = new ArrayList<>();
        if (strArrContains(args, "--cli")) {
            USE_CLI = true;
        } else {
            detector = OsThemeDetector.getDetector();
            setupSwing();
            GuiLoaderFrame.createLoadingThread();
        }
        notMain = false;
        played = new LinkedList<>();
        songQueue = new LinkedList<>();
        database = new DataManager();
        database.loadDatabase();
        database.sortSongList("Default");
        playbackManager = new AudioFilePlaybackBackend();
        playbackManager.setReplayGain(database.getSettings().doSoundCheck());
        if (USE_CLI) {
            Cli.cli(args);
        } else {
            Gui.preLoad();
            Gui.doGui(args);
        }
    }

    // Effects: sets up Swing UI
    private static void setupSwing() {
        detector.registerListener(isDark -> {
            SwingUtilities.invokeLater(() -> {
                uiMod(isDark);
            });
        });
        uiMod(detector.isDark());
    }

    // Effects: changes dark mode status
    private static void uiMod(boolean dark) {
        if (dark) {
            if (SystemUtils.IS_OS_MAC_OSX) {
                FlatMacDarkLaf.setup();
            } else {
                FlatDarculaLaf.setup();
            }
        } else {
            if (SystemUtils.IS_OS_MAC_OSX) {
                FlatMacLightLaf.setup();
            } else {
                FlatIntelliJLaf.setup();
            }
        }
        if (!notMain) {
            Gui.updateUI();
        }
    }

    // Effects: gets theme status
    private static FlatLaf getThemeStatus() {
        try {
            return (FlatLaf) UIManager.getLookAndFeel();
        } catch (ClassCastException e) {
            return new FlatIntelliJLaf();
        }
    }

    // Modifies: audio playback manager
    // Effects:  toggles pause/play status
    private static void togglePlayback() {
        if (playbackManager.paused()) {
            playbackManager.playAudio();
        } else {
            playbackManager.pauseAudio();
        }
    }

    // GUI loader frame
    protected static class GuiLoaderFrame {
        protected static JFrame loadingFrame;
        private static boolean needsSetup = true;

        // Modifies: this
        // Effects:  shows loading pane
        public static void createLoadingThread() {
            if (needsSetup) {
                loadingFrame = new JFrame();
                loadingFrame.setSize(200, 150);
                loadingFrame.setResizable(false);
                loadingFrame.setUndecorated(true);
                loadingFrame.getRootPane().setWindowDecorationStyle(JRootPane.NONE);
                loadingFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                loadingFrame.add(new JLabel("Loading..."));
                loadingFrame.repaint();
                needsSetup = false;
            }
            loadingFrame.setVisible(true);
        }

        // Modifies: this
        // Effects:  hides loading pane
        private static void closeLoadingThread() {
            loadingFrame.setVisible(false);
        }
    }

    // GUI mode
    static class Gui {
        static class Mpris implements MediaPlayer2, Player, Properties {
            private String uid;

            private DBusConnection connection;

            public void playerUpdate(String[] propertyNames) {
                ArrayList<String> removed = new ArrayList<>();
                HashMap<String, Variant<?>> updates = new HashMap<>();
                for (String property : propertyNames) {
                    if (Get("org.mpris.MediaPlayer2.Player", property) == null) {
                        removed.add(property);
                        continue;
                    }
                    switch (property) {
                        case "Metadata": {
                            updates.put(property, new Variant(Get("org.mpris.MediaPlayer2.Player", property), "a{sv}"));
                            break;
                        }
                        default: {
                            updates.put(property, new Variant(Get("org.mpris.MediaPlayer2.Player", property)));
                            break;
                        }
                    }
                }
                try {
                    connection.sendMessage(new PropertiesChanged("/org/mpris/MediaPlayer2",
                            "org.mpris.MediaPlayer2.Player", updates, removed));
                } catch (DBusException e) {
                    throw new RuntimeException(e);
                }
            }

            public Mpris() {
                uid = String.valueOf(Objects.hash(System.currentTimeMillis()));
                try {
                    connection = DBusConnection.getConnection(DBusConnection.DBusBusType.SESSION);
                    connection.exportObject(this);
                    connection.requestBusName("org.mpris.MediaPlayer2.audiodex");
                } catch (DBusException e) {
                    System.err.println(e);
                    System.out.println("Error in MPRIS");
                }
            }

            @Override
            public void Next() {
                playNext();
            }

            @Override
            public void OpenUri(String s) {
                ExceptionIgnore.ignoreExc(() -> playDbFile(new AudioDataStructure(new URI(s).toURL().getFile())));
            }

            @Override
            public void Pause() {
                if (!playbackManager.paused()) {
                    togglePlayback();
                }
            }

            @Override
            public void Play() {
                if (playbackManager.paused()) {
                    togglePlayback();
                }
            }

            @Override
            public void PlayPause() {
                togglePlayback();
            }

            @Override
            public void Previous() {
                playPrevious();
            }

            @Override
            public void Seek(long l) {
                playbackManager.seekTo((l / 1000000.0) + playbackManager.getCurrentTime());
            }

            @Override
            public void Stop() {
                playbackManager.cleanBackend();
                updatePlaybackStatus();
            }

            @Override
            public void Quit() {
                // No
            }

            @Override
            public void Raise() {
                if (miniplayerWindow.isVisible()) {
                    miniplayerWindow.toFront();
                    miniplayerWindow.requestFocus();
                } else {
                    mainWindow.toFront();
                    mainWindow.requestFocus();
                }
            }

            @Override
            public String getObjectPath() {
                return "/org/mpris/MediaPlayer2";
            }

            @Override
            public boolean isRemote() {
                return false;
            }

            @Override
            public <A> A Get(String interfaceName, String propertyName) {
                switch (interfaceName) {
                    case "org.mpris.MediaPlayer2":
                        switch (propertyName) {
                            case "SupportedUriSchemes":
                                return (A) new String[]{"file"};
                            case "SupportedMimeTypes":
                                return (A) new String[]{"audio/flac", "audio/mp3", "audio/mp2", "audio/mp4",
                                        "audio/mpeg", "audio/ogg", "audio/vnd.wave", "audio/x-aifc", "audio/x-aiff"};
                            case "Identity":
                                return (A) "Audiodex";
                            case "DesktopEntry":
                                return (A) ("file://" + System.getProperty("user.home")
                                        + "/.local/share/applications/audiodex.desktop");
                            case "CanQuit":
                                return (A) new Boolean(false);
                            case "Fullscreen":
                                return (A) new Boolean(false);
                            case "CanSetFullscreen":
                                return (A) new Boolean(false);
                            case "HasTrackList":
                                return (A) new Boolean(false);
                            case "CanRaise":
                                return (A) new Boolean(true);
                        }
                    case "org.mpris.MediaPlayer2.Player":
                        switch (propertyName) {
                            case "CanControl":
                                return (A) new Boolean(true);
                            case "CanSeek":
                                return (A) new Boolean(playbackManager.audioIsLoaded());
                            case "CanPause":
                                return (A) new Boolean(playbackManager.audioIsLoaded());
                            case "CanPlay":
                                return (A) new Boolean(playbackManager.audioIsLoaded());
                            case "CanGoPrevious":
                                return (A) new Boolean(!played.isEmpty());
                            case "CanGoNext":
                                return (A) new Boolean(!songQueue.isEmpty());
                            case "MaximumRate":
                                return (A) new Double(1.0);
                            case "MinimumRate":
                                return (A) new Double(1.0);
                            case "Position":
                                return (A) (Long) (long) Math.floor(playbackManager.getCurrentTime() * 1000000);
                            case "Volume":
                                return (A) new Double(1.0);
                            case "PlaybackStatus":
                                return (A) (playbackManager.audioIsLoaded() ?
                                        (playbackManager.paused() ? "Paused" : "Playing") : "Stopped");
                            case "LoopStatus":
                                switch (loop) {
                                    case ALL:
                                        return (A) "Playlist";
                                    case ONE:
                                        return (A) "Track";
                                    case NO:
                                    default:
                                        return (A) "None";
                                }
                            case "Shuffle":
                                return (A) new Boolean(shuffle);
                            case "Metadata": {
                                // More complex
                                if (playbackManager.audioIsLoaded()) {
                                    HashMap<String, Variant<?>> metadata = new HashMap();
                                    metadata.put("mpris:trackid", new Variant(new DBusPath("/audiodex/audioplayback")));
                                    metadata.put("mpris:length", new Variant((long)
                                            Math.floor(playbackManager.getFileDuration() * 1000000)));
                                    if (artworkUrl != null)
                                        metadata.put("mpris:artUrl", new Variant(artworkUrl));
                                    ID3Container container = playbackManager.getID3();
                                    if (container != null) {
                                        if (container.getID3Data("Album") != null)
                                            metadata.put("xesam:album", new Variant(container.getID3Data("Album")));
                                        if (container.getID3Data("AlbumArtist") != null)
                                            metadata.put("xesam:albumArtist", new Variant(new String[]{(String) container.getID3Data("AlbumArtist")}));
                                        if (container.getID3Data("Title") != null)
                                            metadata.put("xesam:title", new Variant(container.getID3Data("Title")));
                                        if (container.getID3Data("Artist") != null)
                                            metadata.put("xesam:artist", new Variant(new String[]{(String) container.getID3Data("Artist")}));
                                        if (container.getID3Data("Lyrics") != null)
                                            metadata.put("xesam:asText", new Variant(container.getID3Data("Lyrics")));
                                        if (container.getID3Data("BPM") != null)
                                            metadata.put("xesam:audioBPM", new Variant(container.getID3Data("BPM")));
                                        if (container.getID3Data("Comment") != null)
                                            metadata.put("xesam:comment", new Variant(new String[]{(String) container.getID3Data("Comment")}));
                                        if (container.getID3Data("Composer") != null)
                                            metadata.put("xesam:composer", new Variant(new String[]{(String) container.getID3Data("Composer")}));
                                        if (container.getID3Data("Track") != null)
                                            metadata.put("xesam:trackNumber", new Variant(container.getID3Data("Track")));
                                        if (container.getID3Data("Disc") != null)
                                            metadata.put("xesam:discNumber", new Variant(container.getID3Data("Disc")));
                                        if (container.getID3Data("GenreString") != null)
                                            metadata.put("xesam:genre", new Variant(new String[]{(String) container.getID3Data("GenreString")}));
                                    }
                                    return (A) metadata;
                                }
                            }
                        }
                }
                return null;
            }

            @Override
            public <A> void Set(String interfaceName, String propertyName, A a) {
                switch (interfaceName) {
                    case "org.mpris.MediaPlayer2.Player":
                        switch (propertyName) {
                            case "LoopStatus": {
                                switch ((String) a) {
                                    case "Playlist": {
                                        loop = LoopType.ALL;
                                        loopButton.setIcon(loopAll);
                                        break;
                                    }
                                    case "Track": {
                                        loop = LoopType.ONE;
                                        loopButton.setIcon(loopOne);
                                        break;
                                    }
                                    case "None":
                                    default: {
                                        loop = LoopType.NO;
                                        loopButton.setIcon(noLoop);
                                        break;
                                    }
                                }
                                break;
                            }
                            case "Shuffle": {
                                shuffle = (Boolean) a;
                                shuffleButton.setIcon(shuffle ? shuffleOn : noShuffle);
                                break;
                            }
                        }
                }
                updateControls();
            }

            @Override
            public Map<String, Variant<?>> GetAll(String interfaceName) {
                HashMap<String, Variant<?>> updates = new HashMap<>();
                String[] propertyNames;
                switch (interfaceName) {
                    case "org.mpris.MediaPlayer2": {
                        propertyNames = new String[]{"SupportedUriSchemes", "SupportedMimeTypes", "Identity",
                                "DesktopEntry", "CanQuit", "Fullscreen", "CanSetFullscreen", "HasTrackList",
                                "CanRaise"};
                        break;
                    }
                    case "org.mpris.MediaPlayer2.Player": {
                        propertyNames = new String[]{"CanControl", "CanSeek", "CanPause", "CanPlay", "CanGoPrevious",
                                "CanGoNext", "MaximumRate", "MinimumRate", "Position", "Volume", "PlaybackStatus",
                                "LoopStatus", "Shuffle", "Metadata"};
                        break;
                    }
                    default:
                        propertyNames = new String[]{};
                }
                for (String property : propertyNames) {
                    switch (property) {
                        case "Metadata": {
                            updates.put(property, new Variant(Get(interfaceName, property), "a{sv}"));
                            break;
                        }
                        default: {
                            updates.put(property, new Variant(Get(interfaceName, property)));
                            break;
                        }
                    }
                } //*/
                return updates;
            }
        }

        private static JFrame activeConversionsView;
        private static JTable activeConversionsTable = new JTable(new ConverterTableModel());

        // Setup conversion view
        private static void setupActiveConversionsView() {
            activeConversionsView = new JFrame("Active Conversions");
            activeConversionsView.add(activeConversionsTable);
            activeConversionsView.setResizable(false);
            activeConversionsView.setAlwaysOnTop(true);
            activeConversionsTable.setRowHeight(32);
            activeConversionsTable.getColumnModel().getColumn(0).setMaxWidth(144);
            activeConversionsTable.getColumnModel().getColumn(0).setMinWidth(144);
            activeConversionsTable.getColumnModel().getColumn(1).setMaxWidth(192);
            activeConversionsTable.getColumnModel().getColumn(1).setMinWidth(192);
            activeConversionsTable.getColumnModel().getColumn(1).setCellRenderer(new ProgressCellRender());
        }

        private static String artworkUrl = null;

        // Effects: hides active conversion view if there are no active conversions, otherwise updates the view
        public static void updateConverterView() {
            if (audioConverterList.isEmpty()) {
                activeConversionsView.setVisible(false);
            } else {
                SwingUtilities.invokeLater(() -> {
                    activeConversionsTable.updateUI();
                    activeConversionsView.pack();
                    if (!activeConversionsView.isVisible()) {
                        activeConversionsView.setVisible(true);
                    }
                });
            }
        }
        
        private enum LoopType {
            NO,
            ONE,
            ALL
        }

        private static LoopType loop = LoopType.NO;
        private static boolean shuffle = false;

        // Song right click menu
        private static class RightClickSongMenu extends JPopupMenu {
            JMenuItem item;

            // Effects: adds metadata editor option
            private void addMetadataEditor(int row) {
                item = new JMenuItem("Edit metadata");
                item.addActionListener(e ->
                        new ID3PopupFrame(database.getAudioFile(row), obj -> {
                            database.getAudioFile(row).updateID3((ID3Container) obj.getValue()); // Updates ID3 Data
                            AudioDecoder decoder = AudioFileLoader.loadFile(database.getAudioFile(row).getFilename());
                            decoder.prepareToPlayAudio();
                            decoder.setID3((ID3Container) obj.getValue());
                            database.sortSongList("Default");
                            musicTable.updateUI();
                        }));
                this.add(item);
            }

            // Effects: adds re-encode option
            private void addReencoder(int row) {
                item = new JMenuItem("Re-encode song");
                item.addActionListener(e ->
                        new ConversionPopupFrame(database.getAudioFile(row), popup -> {
                            audioConverterList.add((AudioConversion) popup.getValue());
                            ((AudioConversion) popup.getValue()).start();
                        }));
                this.add(item);
            }

            // Effects: adds remove option
            private void addRemover(int row) {
                item = new JMenuItem("Remove song");
                item.addActionListener(e ->
                        new ConfirmationPopupFrame("Are you sure you want<br>to remove this song?",
                                ErrorImageTypes.WARNING, popup -> {
                            if ((Boolean) popup.getValue()) {
                                database.removeSongIndex(row);
                                musicList.updateUI();
                            }
                        }, "Yes", "No"));
                this.add(item);
            }

            // Effects: Does the thing
            RightClickSongMenu(int row) {
                addMetadataEditor(row);
                addReencoder(row);
                addRemover(row);
            }
        }
        
        public static void preLoad() {
            setupActiveConversionsView();
            setupMusicTableEvents();
            setupMainWindow();
            fileLabel.putClientProperty("FlatLaf.styleClass", "h3.regular");
            albumLabel.putClientProperty("FlatLaf.styleClass", "medium");
            leftPlaybackLabel.putClientProperty("FlatLaf.styleClass", "mini");
            rightPlaybackLabel.putClientProperty("FlatLaf.styleClass", "mini");
            loadVectors();
            setupButtons();
            makeButtonTransparent(skipButton);
            makeButtonTransparent(prevButton);
            makeButtonTransparent(playButton);
            makeButtonTransparent(shuffleButton);
            makeButtonTransparent(loopButton);
        }

        private static String[] columns = {
                "Title", "Artist", "Album", "Album Artist"
        };

        // Table cell renderer for a progress bar
        public static class ProgressCellRender extends JProgressBar implements TableCellRenderer {
            ProgressCellRender() {
                super(0, 1000);
            }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                int progress = 0;
                if (value instanceof Double) {
                    progress = Math.toIntExact(Math.round(((Double) value) * 1000.0));
                } else if (value instanceof Float) {
                    progress = Math.round(((Float) value) * 1000f);
                } else if (value instanceof Integer) {
                    progress = (int) value;
                }
                setValue(progress);
                return this;
            }
        }

        // Table model to get around Jtable pain
        private static class MusicTableModel extends DefaultTableModel {

            // Gets table row count
            @Override
            public int getRowCount() {
                if (database == null) {
                    return 0;
                }
                return database.audioListSize();
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
                    return String.class;
                }
                return ProgressCellRender.class;
            }

            // Prevents editing
            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return false;
            }

            // Gets table value
            @Override
            public Object getValueAt(int rowIndex, int columnIndex) {
                if (database == null) {
                    return "";
                }
                Object obj =
                        database.getAudioFile(rowIndex).getId3Data().getID3Data(convertValue(columns[columnIndex]));
                if (obj == null) {
                    return "";
                }
                return obj;
            }
        }

        // Table model to get around Jtable pain
        private static class ConverterTableModel extends DefaultTableModel {

            // Gets table row count
            @Override
            public int getRowCount() {
                return audioConverterList.size();
            }

            // Gets table column count
            @Override
            public int getColumnCount() {
                return 2;
            }

            // Gets table column
            @Override
            public String getColumnName(int columnIndex) {
                if (columnIndex == 0) {
                    return "Filename";
                }
                return "Progress";
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
                if (columnIndex == 0) {
                    return new File(audioConverterList.get(rowIndex).getTarget()).getName();
                }
                return audioConverterList.get(rowIndex).getComplete();
            }
        }

        private static JTable musicTable = new JTable(new MusicTableModel());
        private static JScrollPane musicList = new JScrollPane(musicTable);

        // Setup music table events
        private static void setupMusicTableEvents() {
            musicTable.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent evt) {
                    int row = musicTable.rowAtPoint(evt.getPoint());
                    int col = musicTable.columnAtPoint(evt.getPoint());
                    if (row >= 0 && col >= 0) {
                        if (evt.getButton() == 1) {
                            if (evt.getClickCount() == 2) {
                                queueFrom(row);
                                updatePlaybackBar();
                            }
                        } else if (evt.getButton() == 3) {
                            System.out.println("Right click!");
                            RightClickSongMenu rightClickSongMenu = new RightClickSongMenu(row);
                            rightClickSongMenu.show(evt.getComponent(), evt.getX(), evt.getY());
                        }
                    }
                }
            });
            setupMusicTableSortEvents();
        }
        
        private static int sortMode = -1;
        
        // Setup music table header events
        private static void setupMusicTableSortEvents() {
            musicTable.getTableHeader().addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent evt) {
                    int col = musicTable.columnAtPoint(evt.getPoint());
                    if (col >= 0) {
                        if (evt.getButton() == 1) {
                            if (evt.getClickCount() == 1) {
                                if (sortMode == col) {
                                    database.sortSongList("Default");
                                    sortMode = -1;
                                } else {
                                    database.sortSongList(convertValue(columns[col]));
                                    sortMode = col;
                                }
                                updateGuiList();
                            }
                        }
                    }
                }
            });
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

        private static void updateMpris(String[] toUpdate) {
            if (mpris != null) {
                mpris.playerUpdate(toUpdate);
            }
        }

        // Effects: loads image from data directory (/data/spec or (jar)/data)
        private static BufferedImage loadImage(String filename) throws IOException {
            try {
                BufferedImage img = ImageIO.read(Main.class.getResourceAsStream("/data/" + filename));
                System.out.println(img);
                return img;
            } catch (Exception ex) {
                try {
                    return ImageIO.read(new File("data/spec/" + filename));
                } catch (Exception e) {
                    throw new IOException("Couldn't load image: " + e.getMessage() + " and " + ex.getMessage());
                }
            }
        }

        // Effects: loads font from data directory (/data/spec or (jar)/data)
        private static Font loadFont(String filename) throws IOException {
            try {
                return Font.createFont(Font.TRUETYPE_FONT,
                        Main.class.getResourceAsStream("/data/" + filename));
            } catch (Exception ex) {
                try {
                    return Font.createFont(Font.TRUETYPE_FONT,
                            new File("data/spec/" + filename));
                } catch (Exception e) {
                    throw new IOException("Couldn't load font: " + e.getMessage() + " and " + ex.getMessage());
                }
            }
        }

        private static JFrame mainWindow;
        private static JFrame miniplayerWindow;
        private static Mpris mpris;

        // Setup main window close action
        private static void setupMainWindow() {
            mainWindow = new JFrame("Audiodex");
            if (SystemUtils.IS_OS_LINUX) {
                try {
                    mpris = new Mpris();
                } catch (Throwable e) {
                    System.err.println(e);
                    System.out.println("MPRIS failed to load for some reason.");
                }
            }
            mainWindow.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    if (database.beenModified()) {
                        if (database.getSettings().doSaveOnClose()) {
                            database.saveDatabaseFile();
                            quitApp();
                        } else {
                            new ConfirmationPopupFrame("Database has been modified<br>since last save.<br>"
                                    + "Save before quitting?", ErrorImageTypes.WARNING, popup -> {
                                database.saveDatabaseFile();
                                quitApp();
                            }, popup -> quitApp(), "Yes", "No");
                        }
                    } else quitApp();
                }
            });
        }
        
        private static void quitApp() {
            new Thread(() -> {
                mainWindow.setVisible(false);
                if (!audioConverterList.isEmpty()) {
                    ExceptionIgnore.ignoreExc(() -> SwingUtilities.invokeAndWait(() ->
                            new ErrorPopupFrame("Waiting for encoders<br>to finish...",
                                    ErrorImageTypes.INFO, null).updateUI()));
                    while (!audioConverterList.isEmpty()) {
                        waitForFirstEncoder();
                    }
                }
                mainWindow.dispose();
                EventLog.getInstance().iterator().forEachRemaining(event ->
                        AnsiConsole.out().println(String.format("%s: %s",
                                event.getDate().toString(), event.getDescription())));
                exit(0);
            }).start();
        }

        private static int miniplayerHeight = -1;

        // Setup miniplayer window
        private static void setupMiniplayerWindow() {
            miniplayerWindow = new JFrame("Audiodex Miniplayer");
            ExceptionIgnore.ignoreExc(() -> miniplayerWindow.setIconImage(mainWindow.getIconImage()));
            miniplayerWindow.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
            miniplayerWindow.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    miniplayerWindow.setVisible(false);
                    mainWindow.add(musicPlaybackView);
                    setupMainWindowLayout();
                    mainWindow.setLocation(miniplayerWindow.getLocation());
                    mainWindow.setVisible(true);
                }
            });
            miniplayerWindow.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    super.componentResized(e);
                    miniplayerWindow.setSize(miniplayerWindow.getWidth(), miniplayerHeight);
                }
            });
        }

        // Loads miniplayer
        private static void miniplayerLoad() {
            mainWindow.setVisible(false);
            miniplayerWindow.add(musicPlaybackView);
            miniplayerWindow.pack();
            miniplayerHeight = miniplayerWindow.getHeight();
            miniplayerWindow.setSize(320, miniplayerHeight);
            miniplayerWindow.setMaximumSize(new Dimension(Integer.MAX_VALUE, miniplayerHeight));
            miniplayerWindow.setMinimumSize(new Dimension(256, miniplayerHeight));
            miniplayerWindow.setMaximizedBounds(new Rectangle(Integer.MAX_VALUE, miniplayerHeight));
            miniplayerWindow.setLocation(mainWindow.getLocation());
            miniplayerWindow.setVisible(true);
        }

        // Effects: waits for first audio encoding thread to finish
        private static void waitForFirstEncoder() {
            if (audioConverterList.isEmpty()) {
                return;
            }
            audioConverterList.get(0).waitForEncoderFinish();
        }

        // Modifies: this
        // Effects:  displays main window
        public static void doGui(String[] args) {
            createPlaybackBar();
            createControlsView();
            createMusicView();
            visualizerThread = new PlaybackThread();
            visualizerThread.start();
            musicArt.setIcon(placeholder);
            playFileFromArgs(args);
            mainWindow.setSize(900, 500);
            mainWindow.setResizable(true);
            ExceptionIgnore.ignoreExc(() -> mainWindow.setIconImage(loadImage("AppIcon.png")));
            updateGuiList();
            mainWindow.add(musicPlaybackView);
            mainWindow.add(musicList);
            setupMainWindowLayout();
            setupMenubar();
            updatePlaybackBar();
            setupMiniplayerWindow();
            updateUI();
            GuiLoaderFrame.closeLoadingThread();
            playFileFromArgsAfter();
        }
        
        // Removed file error frame
        private static void replaceRemovedAudioFiles() {
            List<Integer> indexes = database.getRemovedAudioFiles();
        }
        
        // Plays audio file passed in as input
        private static void playFileFromArgsAfter() {
            if (playbackManager.audioIsLoaded()) {
                miniplayerLoad();
            } else {
                mainWindow.setVisible(true);
            }
        }
        
        // Plays audio file passed in as input
        private static void playFileFromArgs(String[] args) {
            for (String arg : args) {
                if (new File(arg).exists()) {
                    AudioDataStructure structure = new AudioDataStructure(arg);
                    if (!structure.isEmpty()) {
                        playDbFile(structure);
                        return; // Found the file! done
                    }
                }
            }
        }

        private List<PopupManager.Popup> popupList = new LinkedList<>();

        // Effects: refreshes all UI elements
        public static void updateUI() {
            ExceptionIgnore.ignoreExc(() -> {
                SwingUtilities.updateComponentTreeUI(mainWindow);
                SwingUtilities.updateComponentTreeUI(activeConversionsView);
                SwingUtilities.updateComponentTreeUI(GuiLoaderFrame.loadingFrame);
                PopupManager.updatePopupTrees();
            });
            updateControls();
        }

        // Effects: updates control icons
        public static void updateControls() {
            playButton.setIcon(playbackManager.paused() ? play : pause);
            UIDefaults defaults = getThemeStatus().getDefaults();
            prevButton.setForeground(played.isEmpty() ? defaults.getColor("Button.disabledText") :
                    defaults.getColor("RootPane.foreground"));
            prevButton.setEnabled(!played.isEmpty());
            skipButton.setForeground(songQueue.isEmpty() ? defaults.getColor("Button.disabledText") :
                    defaults.getColor("RootPane.foreground"));
            skipButton.setEnabled(!songQueue.isEmpty());
        }

        // Modifies: this
        // Effects:  handle when a song just finished playing
        public static void songFinishedPlaying() {
            if (loop == LoopType.ONE) {
                playDbFile(nowPlaying);
                updateControls();
                return;
            }
            if (songQueue.isEmpty()) {
                if (loop == LoopType.ALL) {
                    playDbFile(database.getAudioFile(0));
                    queueFrom(0);
                } else {
                    setPlaybackBarLength(-1);
                    played.addFirst(nowPlaying);
                    nowPlaying = null;
                }
            } else {
                playNext();
            }
            maxBoundPlayedList();
            updatePlaybackBar();
        }

        // Modifies: this
        // Effects:  plays most recently played song, if possible
        private static void playPrevious() {
            if (played.isEmpty()) {
                AnsiConsole.out().println("No previous song to play!");
                return;
            }
            if (nowPlaying != null) {
                songQueue.addFirst(nowPlaying);
            }
            playDbFile(played.getFirst());
            played.removeFirst();
            updatePlaybackBar();
        }

        // Modifies: this
        // Effects:  plays next song, if possible
        private static void playNext() {
            if (songQueue.isEmpty()) {
                AnsiConsole.out().println("No song in queue to play!");
                return;
            }
            // If song queue isn't empty we're playing SOMETHING
            played.addFirst(nowPlaying);
            playDbFile(songQueue.getFirst());
            songQueue.removeFirst();
            updatePlaybackBar();
        }

        private static JMenuBar mainMenuBar;

        // Set up main menu bar;
        // this is way too long to use a function, and splitting makes no sense
        static {
            mainMenuBar = new JMenuBar();
            JMenu menu = new JMenu("File");
            JMenuItem item = new JMenuItem("Play file");
            item.addActionListener(e -> playArbitraryFile());
            menu.add(item);
            item = new JMenuItem("Re-encode file");
            item.addActionListener(e ->
                    new FilePopupFrame(fileSystemView.getDefaultDirectory().getAbsolutePath(),
                            AudioFileLoader.KNOWN_FILETYPES, popup -> {
                        AudioDataStructure structure = new AudioDataStructure((String) popup.getValue());
                        if (!structure.isEmpty()) {
                            new ConversionPopupFrame(structure, converter -> {
                                audioConverterList.add((AudioConversion) converter.getValue());
                                ((AudioConversion) converter.getValue()).start();
                            });
                        } else {
                            new PopupManager.ErrorPopupFrame("File is corrupt or in an<br>unsupported format."
                                    + "<br>Cannot re-encode file.",
                                    ErrorImageTypes.ERROR, obj -> { });
                        }
                    }));
            menu.add(item);
            item = new JMenuItem("Settings");
            item.addActionListener(e ->
                    new SettingsFrame(database.getSettings(), new SettingsFrame.Responder[]{
                            () -> playbackManager.setReplayGain(database.getSettings().doSoundCheck())
                    }));
            menu.add(item);
            mainMenuBar.add(menu);
            menu = new JMenu("Database");
            item = new JMenuItem("Add file to database");
            item.addActionListener(e -> addFile());
            menu.add(item);
            item = new JMenuItem("Add directory to database");
            item.addActionListener(e -> addDir());
            menu.add(item);
            menu.addSeparator();
            item = new JMenuItem("Save database");
            item.addActionListener(e -> database.saveDatabaseFile());
            menu.add(item);
            menu.addSeparator();
            item = new JMenuItem("Database Manager");
            item.addActionListener(e -> {
                new ConfirmationPopupFrame("<span style=\"color: red\">Here be dragons...</span><br>Are you "
                        + "<i>sure</i> you<br>want to continue?",
                        ErrorImageTypes.HALT, popup -> {
                    DatabaseManagerFrame databaseManagerFrame = new DatabaseManagerFrame(database);
                }, "Yes", "No");
            });
            menu.add(item);
            item = new JMenuItem("Change Database");
            item.addActionListener(e -> {
                if (database.beenModified()) {
                    if (database.getSettings().doSaveOnClose()) {
                        database.saveDatabaseFile();
                        moveDbFolder();
                    } else {
                        new ConfirmationPopupFrame("Database has been modified<br>since last save.<br>"
                                + "Save before loading?", ErrorImageTypes.WARNING, popup -> {
                            database.saveDatabaseFile();
                            moveDbFolder();
                        }, popup -> moveDbFolder(), "Yes", "No");
                    }
                } else {
                    moveDbFolder();
                }
            });
            menu.add(item);
            mainMenuBar.add(menu);
            menu = new JMenu("View");
            item = new JMenuItem("Use Miniplayer");
            item.addActionListener(e -> miniplayerLoad());
            menu.add(item);
            mainMenuBar.add(menu);
        }

        // Modifies: this
        // Effects:  moves database folder and attempts to load file
        private static void moveDbFolder() {
            new FilePopupFrame(fileSystemView.getDefaultDirectory().getAbsolutePath(),
                    new String[]{".folder", ".empty"}, popup -> {
                String path = new File((String) popup.getValue()).getAbsolutePath();
                if (!path.endsWith(String.valueOf(separatorChar))) {
                    path += separatorChar;
                }
                GuiLoaderFrame.createLoadingThread();
                database.setUserDir(path);
                database.loadDatabase();
                updateGuiList();
                GuiLoaderFrame.closeLoadingThread();
            });
        }

        // Modifies: this
        // Effects:  sets up menu bar for main window
        private static void setupMenubar() {
            if (SystemUtils.IS_OS_MAC_OSX) {
                mainWindow.getRootPane().putClientProperty("apple.awt.fullscreenable", true);
                System.setProperty("apple.laf.useScreenMenuBar", "true");
            } else {
                System.setProperty("flatlaf.menuBarEmbedded", "true");
            }
            mainWindow.setJMenuBar(mainMenuBar);
        }

        // Effects: adds file to database via popup
        private static void playArbitraryFile() {
            FilePopupFrame filePopupFrame = new FilePopupFrame(fileSystemView.getDefaultDirectory().getAbsolutePath(),
                    AudioFileLoader.KNOWN_FILETYPES, popup -> {
                AudioDataStructure structure = new AudioDataStructure((String) popup.getValue());
                if (!structure.isEmpty()) {
                    playDbFile(structure);
                    updatePlaybackBar();
                } else {
                    new PopupManager.ErrorPopupFrame("File is corrupt or in an<br>unsupported format."
                            + "<br>Cannot play file.",
                            ErrorImageTypes.ERROR, obj -> { });
                }
            });
        }

        // Effects: adds file to database via popup
        private static void addFile() {
            FilePopupFrame filePopupFrame = new FilePopupFrame(fileSystemView.getDefaultDirectory().getAbsolutePath(),
                    AudioFileLoader.KNOWN_FILETYPES, popup -> {
                        GuiLoaderFrame.createLoadingThread();
                        database.addFileToSongDatabase(new File((String) popup.getValue()).getAbsolutePath());
                        database.sanitizeAudioDatabase();
                        database.sortSongList("Default");
                        updateGuiList();
                        GuiLoaderFrame.closeLoadingThread();
                        if (database.getSettings().doSaveOnImport()) database.saveDatabaseFile();
                    });
        }

        // Effects: adds directory to database via popup
        private static void addDir() {
            FilePopupFrame filePopupFrame = new FilePopupFrame(fileSystemView.getDefaultDirectory().getAbsolutePath(),
                    new String[]{".folder"}, popup -> {
                GuiLoaderFrame.createLoadingThread();
                database.addDirToSongDatabase(new File((String) popup.getValue()).getAbsolutePath());
                database.sanitizeAudioDatabase();
                database.sortSongList("Default");
                updateGuiList();
                GuiLoaderFrame.closeLoadingThread();
                if (database.getSettings().doSaveOnImport()) database.saveDatabaseFile();
            });
        }

        // Modifies: this
        // Effects:  sets up grid bag layout for main window
        private static void setupMainWindowLayout() {
            GridBagLayout layout = new GridBagLayout();
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.weightx = 1.0;
            constraints.anchor = GridBagConstraints.FIRST_LINE_START;
            constraints.gridx = 0;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.gridy = 0;
            layout.setConstraints(musicPlaybackView, constraints);
            constraints.gridy++;
            constraints.fill = GridBagConstraints.BOTH;
            constraints.weighty = 1.0;
            layout.setConstraints(musicList, constraints);
            mainWindow.setLayout(layout);
        }

        // Modifies: button
        // Effects:  makes button background transparent
        private static void makeButtonTransparent(JButton button) {
            button.setBorder(new EmptyBorder(0, 0, 0, 0));
            button.setBorderPainted(false);
            button.setContentAreaFilled(false);
            button.setOpaque(false);
            button.setDisabledIcon(button.getIcon());
        }

        // Effects: converts ID3 tag to its corresponding menu bar item
        private static String convertKey(String key) {
            switch (key) {
                case "AlbumArtist":
                    return "Album Artist";
                default:
                    return key;
            }
        }

        // Effects: converts menu bar item to its corresponding ID3 tag
        private static String convertValue(String value) {
            switch (value) {
                case "Album Artist":
                    return "AlbumArtist";
                default:
                    return value;
            }
        }

        // Modifies: this
        // Effects:  updates the list
        public static void updateGuiList() {
            musicList.updateUI();
        }

        private static JSlider playbackSlider = new JSlider(0, 0);
        private static JLabel leftPlaybackLabel = new JLabel("X:XX");
        private static JLabel rightPlaybackLabel = new JLabel("X:XX");
        private static JPanel playbackStatusView = new JPanel(true);
        private static JPanel musicPlaybackView = new JPanel(true);
        private static JPanel controlsView = new JPanel(true);
        private static JLabel musicArt = new JLabel();
        private static JLabel fileLabel = new JLabel();
        private static JLabel albumLabel = new JLabel();
        private static JButton skipButton;
        private static JButton prevButton;
        private static JButton playButton;
        private static JButton shuffleButton;
        private static JButton loopButton;
        private static JVectorIcon skip = null;
        private static JVectorIcon prev = null;
        private static JVectorIcon placeholder = null;
        private static JVectorIcon play = null;
        private static JVectorIcon pause = null;
        private static JVectorIcon noLoop = null;
        private static JVectorIcon loopOne = null;
        private static JVectorIcon loopAll = null;
        private static JVectorIcon noShuffle = null;
        private static JVectorIcon shuffleOn = null;

        // Prepare visual elements
        private static void loadVectors() {
            try {
                placeholder = new JVectorIcon(loadVector("music.svg"), 64, 64);
                skip = new JVectorIcon(loadVector("forward.svg"), 24, 16);
                prev = new JVectorIcon(loadVector("backward.svg"), 24, 16);
                play = new JVectorIcon(loadVector("play.svg"), 48, 32);
                pause = new JVectorIcon(loadVector("pause.svg"), 48, 32);
                noLoop = new JVectorIcon(loadVector("subdirectory.svg"), 24, 16);
                loopOne = new JVectorIcon(loadVector("flip-backward.svg"), 24, 16);
                loopAll = new JVectorIcon(loadVector("loop.svg"), 24, 16);
                noShuffle = new JVectorIcon(loadVector("forward-arrow.svg"), 24, 16);
                shuffleOn = new JVectorIcon(loadVector("shuffle.svg"), 24, 16);
            } catch (IOException e) {
                ExceptionIgnore.logException(e);
            }
        }

        // Set up buttons
        private static void setupButtons() {
            skipButton = new JButton(skip);
            prevButton = new JButton(prev);
            playButton = new JButton(play);
            skipButton.addActionListener(e -> playNext());
            skipButton.setVerticalAlignment(SwingConstants.TOP);
            skipButton.setMaximumSize(new Dimension(24, 16));
            skipButton.setMinimumSize(new Dimension(24, 16));
            skipButton.setEnabled(false);
            prevButton.addActionListener(e -> playPrevious());
            prevButton.setVerticalAlignment(SwingConstants.TOP);
            prevButton.setMaximumSize(new Dimension(24, 16));
            prevButton.setMinimumSize(new Dimension(24, 16));
            prevButton.setEnabled(false);
            playButton.setVerticalAlignment(SwingConstants.TOP);
            playButton.setMaximumSize(new Dimension(48, 32));
            playButton.setMinimumSize(new Dimension(48, 32));
            playButton.addActionListener(e -> togglePlayback());
            makeShuffleButton();
            makeLoopButton();
        }
        
        // Effects: sets up shuffle button
        private static void makeShuffleButton() {
            shuffleButton = new JButton(noShuffle);
            shuffleButton.addActionListener(e -> {
                toggleShuffle();
                shuffleButton.setIcon(shuffle ? shuffleOn : noShuffle);
            });
            shuffleButton.setVerticalAlignment(SwingConstants.TOP);
            shuffleButton.setMaximumSize(new Dimension(24, 16));
            shuffleButton.setMinimumSize(new Dimension(24, 16));
        }
        
        // Effects: sets up loop button
        private static void makeLoopButton() {
            loopButton = new JButton(noLoop);
            loopButton.setVerticalAlignment(SwingConstants.TOP);
            loopButton.setMaximumSize(new Dimension(24, 16));
            loopButton.setMinimumSize(new Dimension(24, 16));
            loopButton.addActionListener(e -> {
                switch (loop) {
                    case NO:
                        loop = LoopType.ALL;
                        loopButton.setIcon(loopAll);
                        break;
                    case ALL:
                        loop = LoopType.ONE;
                        loopButton.setIcon(loopOne);
                        break;
                    case ONE:
                        loop = LoopType.NO;
                        loopButton.setIcon(noLoop);
                        break;
                }
            });
        }

        // Modifies: this
        // Effects:  toggles play/pause status
        private static void togglePlayback() {
            if (playbackManager.paused()) {
                playbackManager.playAudio();
            } else {
                playbackManager.pauseAudio();
            }
            updateControls();
        }

        // Effects: sets up the music controls view
        public static void createControlsView() {
            controlsView.add(skipButton);
            controlsView.add(prevButton);
            controlsView.add(playButton);
            controlsView.add(shuffleButton);
            controlsView.add(loopButton);
            GridBagLayout layout = new GridBagLayout();
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = 1;
            constraints.gridy = 2;
            layout.setConstraints(loopButton, constraints);
            constraints.gridy = 0;
            layout.setConstraints(skipButton, constraints);
            constraints.gridx = 0;
            layout.setConstraints(prevButton, constraints);
            constraints.gridy = 2;
            layout.setConstraints(shuffleButton, constraints);
            constraints.gridy = 1;
            constraints.gridwidth = 2;
            constraints.weightx = 1;
            layout.setConstraints(playButton, constraints);
            controlsView.setLayout(layout);
        }

        // Effects: sets up the music playback view
        public static void createMusicView() {
            musicPlaybackView.add(musicArt);
            musicPlaybackView.add(fileLabel);
            musicPlaybackView.add(albumLabel);
            musicPlaybackView.add(playbackStatusView);
            musicPlaybackView.add(controlsView);
            createMusicLayout();
        }

        // Effects: helper for createMusicView()
        private static void createMusicLayout() {
            GridBagLayout layout = new GridBagLayout();
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.weighty = 1.0;
            constraints.gridy = 0;
            constraints.gridx = 2;
            constraints.gridheight = 3;
            layout.setConstraints(controlsView, constraints);
            constraints.gridx = 0;
            layout.setConstraints(musicArt, constraints);
            constraints.weightx = 1.0;
            constraints.gridheight = 1;
            constraints.gridy = 1;
            constraints.gridx = 1;
            layout.setConstraints(fileLabel, constraints);
            constraints.gridy = 2;
            layout.setConstraints(albumLabel, constraints);
            constraints.gridx = 0;
            constraints.gridy = 3;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.gridwidth = 4;
            layout.setConstraints(playbackStatusView, constraints);
            musicPlaybackView.setLayout(layout);
        }

        // Modifies: this
        // Effects:  queues songs in the order of the list from the selected element
        //           also starts first song
        private static void queueFrom(int index) {
            int myIndex = index;
            new Thread(() -> {
                playDbFile(database.getAudioFile(myIndex));
                updatePlaybackBar();
            }).start();
            queueFromNoStart(index);
        }

        // Modifies: this
        // Effects:  queues songs in the order of the list from the selected element
        private static void queueFromNoStart(int index) {
            int myIndex = index;
            songQueue.clear();
            if (shuffle) {
                for (index = 0; index < database.audioListSize(); index++) {
                    if (index == myIndex) {
                        continue;
                    }
                    songQueue.add((int)Math.floor(Math.random() * songQueue.size()), database.getAudioFile(index));
                }
            } else {
                for (index++; index < database.audioListSize(); index++) {
                    songQueue.addLast(database.getAudioFile(index));
                }
            }
        }

        // Effects: toggles shuffle function
        //          also reloads song queue
        private static void toggleShuffle() {
            shuffle = !shuffle;
            if (nowPlaying != null) {
                for (int i = 0; i < database.audioListSize(); i++) {
                    if (nowPlaying.equals(database.getAudioFile(i))) {
                        queueFromNoStart(i);
                        updateControls();
                        return;
                    }
                }
            }
            for (AudioDataStructure queued : songQueue) {
                for (int i = 0; i < database.audioListSize(); i++) {
                    if (queued.equals(database.getAudioFile(i))) {
                        queueFromNoStart(i);
                        updateControls();
                        return;
                    }
                }
            }
        }
        
        private static AudioDataStructure loadedMusicIcon = null;

        // Effects: updates music playback view
        public static void updatePlaybackBar() {
            fileLabel.setText(playbackManager.getPlaybackString(false));
            String artistLabel = "";
            Object obj = playbackManager.getID3().getID3Data("Artist");
            if (obj != null && !obj.toString().isEmpty()) {
                artistLabel = playbackManager.getID3().getID3Data("Artist").toString();
            }
            obj = playbackManager.getID3().getID3Data("Album");
            if (obj != null && !obj.toString().isEmpty()) {
                if (artistLabel.length() != 0) {
                    artistLabel += " - ";
                }
                artistLabel += playbackManager.getID3().getID3Data("Album").toString();
            }
            albumLabel.setText(artistLabel);
            updateControls();
            updateMpris(new String[] {"PlaybackStatus", "LoopStatus", "Shuffle", "Metadata", "CanGoNext", "CanGoPrevious", "CanPlay", "CanPause", "CanSeek"});
            if (loadedMusicIcon != nowPlaying) {
                updateMusicIcon();
                loadedMusicIcon = nowPlaying;
            }
        }
        
        private static Icon artIcon;
        
        // Effects: sets music icon
        private static void updateMusicIcon() {
            BufferedImage bufferedImage = playbackManager.getArtwork();
            artworkUrl = null;
            if (bufferedImage != null) {
                artIcon = new ImageIcon(new GenerativeResolutionImage(bufferedImage, 64, 64));
                new Thread(() -> {
                    try {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        ImageIO.write(bufferedImage, "PNG", out);
                        byte[] bytes = out.toByteArray();
                        artworkUrl = "data:image/png;base64,"
                                + Base64.getEncoder().encodeToString(bytes);
                        updateMpris(new String[] {"Metadata"});
                    } catch (Exception e) {
                        System.err.println(e);
                    }}).run();
            } else {
                artIcon = placeholder;
            }
            musicArt.setIcon(artIcon);
            musicArt.setPreferredSize(new Dimension(64, 64));
        }

        // Effects: sets up the playback bar
        public static void createPlaybackBar() {
            playbackSlider.addChangeListener(e -> {
                if (playbackSlider.getValueIsAdjusting()) {
                    playbackManager.seekTo(playbackSlider.getValue() / 1000.0);
                }
            });
            playbackStatusView.add(leftPlaybackLabel);
            playbackStatusView.add(playbackSlider);
            playbackStatusView.add(rightPlaybackLabel);
            setupPlaybackBarLayout();
            playbackStatusView.setBorder(new EmptyBorder(0,4,0,4));
        }

        // Modifies: this
        // Effects:  sets up the layout of the playback bar
        private static void setupPlaybackBarLayout() {
            GridBagLayout layout = new GridBagLayout();
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridy = 1;
            layout.setConstraints(leftPlaybackLabel, constraints);
            layout.setConstraints(rightPlaybackLabel, constraints);
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.weightx = 1.0;
            layout.setConstraints(playbackSlider, constraints);
            playbackStatusView.setLayout(layout);
        }

        // Modifies: this
        // Effects:  sets playback bar length
        public static void setPlaybackBarLength(double fileTime) {
            if (fileTime == -1) {
                playbackSlider.setMaximum(0);
            } else { // Dies at a month long but why bother
                playbackSlider.setMaximum((int) (fileTime * 1000));
            }
            rightPlaybackLabel.setText(formatTime((long) fileTime));
            playbackStatusView.updateUI();
            if (!visualizerThread.isAlive()) {
                visualizerThread = new PlaybackThread();
                visualizerThread.start();
            }
        }

        // Modifies: this
        // Effects:  sets the playback bar value and times
        public static void updatePlaybackBarStatus(double time) {
            if (time == -1) {
                playbackSlider.setValue(0);
            } else if (!playbackSlider.getValueIsAdjusting()) {
                playbackSlider.setValue((int) (time * 1000));
            } // Dies at a month long but why bother
            leftPlaybackLabel.setText(formatTime((long) time));
            playbackStatusView.updateUI();
        }

        // Modifies: playbackManager and its decoding thread
        // Effects:  plays a file from database
        private static void playDbFile(AudioDataStructure audioDataStructure) {
            File f = new File(audioDataStructure.getFilename());
            if (f.isFile()) {
                playbackManager.loadAudio(f.getAbsolutePath(), audioDataStructure);
                nowPlaying = audioDataStructure;
                setPlaybackBarLength(playbackManager.getFileDuration());
                playbackManager.startAudioDecoderThread();
                playbackManager.playAudio();
                id3 = playbackManager.getID3();
                audioDataStructure.updateID3(id3); // Update on file load
            } else {
                System.out.println("File no longer exists, or is currently inaccessible.");
            }
        }

        private static PlaybackThread visualizerThread;

        // No other class needs to know this
        // class for the thread which handles the playback indicator
        // in CLI mode
        private static class PlaybackThread extends Thread {
            private boolean run = true;

            // Modifies: this
            // Effects:  ends thread
            public void killThread() {
                run = false;
                safeJoin();
            }

            // Effects: join() but no try-catch
            public void safeJoin() {
                ExceptionIgnore.ignoreExc(() -> join());
            }

            // Effects: join(long millis) but no try-catch
            public void safeJoin(long millis) {
                ExceptionIgnore.ignoreExc(() -> join(millis));
            }

            // Effects: join(long millis, int nanos) but no try-catch
            public void safeJoin(long millis, int nanos) {
                ExceptionIgnore.ignoreExc(() -> join(millis, nanos));
            }

            // Effects: updates the playback status bar (also in charge of the converter view bc why not)
            @Override
            public void run() {
                while (run) {
                    Cli.wait(20);
                    if (playbackManager == null) {
                        continue;
                    }
                    updatePlaybackBarStatus(playbackManager.getCurrentTime());
                    updateConverterView(); // Do this here bc why not
                }
            }
        }
    }
    //*///

    // CLI mode
    private static class Cli {
        private enum MenuState {
            CLI_MAINMENU,
            CLI_BROWSEMENU,
            CLI_OTHER
        }

        private static MenuState state = MenuState.CLI_OTHER;

        private static PlaybackThread visualizerThread;

        // No other class needs to know this
        // class for the thread which handles the playback indicator
        // in CLI mode
        private static class PlaybackThread extends Thread {
            private boolean run = true;

            // Modifies: this
            // Effects:  ends thread
            public void killThread() {
                run = false;
                safeJoin();
            }

            // Effects: join() but no try-catch
            public void safeJoin() {
                ExceptionIgnore.ignoreExc(() -> join());
            }

            // Effects: join(long millis) but no try-catch
            public void safeJoin(long millis) {
                ExceptionIgnore.ignoreExc(() -> join(millis));
            }

            // Effects: join(long millis, int nanos) but no try-catch
            public void safeJoin(long millis, int nanos) {
                ExceptionIgnore.ignoreExc(() -> join(millis, nanos));
            }

            // Effects: plays audio in file loadedFile
            @Override
            public void run() {
                if (AnsiConsole.getTerminalWidth() == 0) {
                    return;
                }
                while (run) {
                    Cli.wait(20);
                    if (playbackManager == null) {
                        return;
                    }
                    doPlaybackStatusWrite();
                }
            }
        }

        // main function for the CLI
        private static void cli(String[] args) {
            AnsiConsole.systemInstall();
            if (AnsiConsole.getTerminalWidth() == 0) {
                AnsiConsole.out().println("This application works better in a regular java console.");
            }
            Scanner inputScanner = new Scanner(System.in);
            updateAllFiles(inputScanner);
            database.removeEmptyAudioFiles();
            visualizerThread = new PlaybackThread();
            visualizerThread.start();
            while (true) {
                state = MenuState.CLI_MAINMENU;
                printMenu();
                doPlaybackStatusWrite();
                cliMain(inputScanner, inputScanner.nextLine());
                if (end) {
                    state = MenuState.CLI_OTHER;
                    waitForEncoders();
                    AnsiConsole.out().println("Goodbye!");
                    return;
                }
            }
        }

        // Modifies: console and filesystem
        // Effects:  creates a list of files, dumps it to the filesystem
        //           and then waits for several seconds
        private static void listFiles() {
            StringBuilder fileOut = new StringBuilder();
            for (int i = 0; i < database.audioListSize(); i++) {
                AnsiConsole.out().println(database.getAudioFile(i).getPlaybackString());
                fileOut.append(database.getAudioFile(i).getPlaybackString()).append("\n");
            }
            FileManager.writeToFile(System.getProperty("user.home") + "/audiodex/files.txt", fileOut.toString());
            AnsiConsole.out().println("\nA file has been output to " + System.getProperty("user.home")
                    + "/audiodex/files.txt.");
            wait(2500);
        }

        // Modifies: this
        // Effects:  requests filename updates for all unlocatable files
        private static void updateAllFiles(Scanner inputScanner) {
            List<Integer> indexes = database.getRemovedAudioFiles();
            if (indexes.size() == 0) {
                return;
            }
            for (Integer index : indexes) {
                String in;
                do {
                    AnsiConsole.out().println("Couldn't find the file " + database.getAudioFile(index).getFilename()
                            + ", update index? (Y/n)");
                    in = inputScanner.nextLine().toLowerCase().trim();
                    if (in.equals("n")) {
                        break;
                    }
                    AnsiConsole.out().println("Please enter the new filename.");
                    database.updateAudioFile(index, inputScanner.nextLine().trim());
                } while (database.getAudioFile(index).isEmpty()
                        || !new File(database.getAudioFile(index).getFilename()).exists());
            }
        }

        // Effects: waits for all audio encoding threads to finish
        private static void waitForEncoders() {
            if (audioConverterList.isEmpty()) {
                return;
            }
            AnsiConsole.out().println("Waiting for audio converter threads to finish...");
            while (!audioConverterList.isEmpty()) {
                waitForFirstEncoder();
            }
        }

        // Effects: waits for first audio encoding thread to finish
        private static void waitForFirstEncoder() {
            if (audioConverterList.isEmpty()) {
                return;
            }
            audioConverterList.get(0).waitForEncoderFinish();
        }

        // Modifies: this
        // Effects:  finishedSong() extension for CLI
        private static void finishedSong() {
            if (loop) {
                playDbFile(played.getFirst());
                played.removeFirst();
            } else if (!songQueue.isEmpty()) {
                playDbFile(songQueue.getFirst());
                songQueue.removeFirst();
            }
            switch (state) {
                case CLI_BROWSEMENU:
                    printBrowseMenu();
                    break;
                case CLI_MAINMENU:
                    printMenu();
                    break;
            }
            doPlaybackStatusWrite();
        }

        // Modifies: this
        // Effects:  finishedEncode() extension for CLI
        private static void finishedEncode() {
            if (Objects.requireNonNull(state) == MenuState.CLI_MAINMENU) {
                printMenu();
            }
        }

        @SuppressWarnings("methodlength") // Large switch/case
        // CLI switch manager
        // calls corresponding functions for different actions
        private static void cliMain(Scanner inputScanner, String selected) {
            state = MenuState.CLI_OTHER;
            switch (selected.toLowerCase()) {
                case "1":
                    playFile(inputScanner);
                    break;
                case "2":
                    addDatabaseFile(inputScanner);
                    break;
                case "3":
                    addDatabaseDir(inputScanner);
                    break;
                case "4":
                    browseMenu(inputScanner);
                    break;
                case "5":
                    database.saveDatabaseFile();
                    break;
                case "6":
                    startEncoder(inputScanner);
                    break;
                case "c":
                    playbackManager.playAudio();
                    break;
                case "p":
                    playbackManager.pauseAudio();
                    break;
                case "m":
                    dbMenu(inputScanner);
                    break;
                case "9":
                    listFiles();
                    break;
                case "s":
                    seekAudio(inputScanner);
                    break;
                case "<":
                    playPrevious();
                    break;
                case ">":
                    playNext();
                    break;
                case "q":
                    cleanup();
                    return;
                case "d":  // Database uses absolute file paths, otherwise it would fail to load audio
                    database.addFileToSongDatabase((new File(filename)).getAbsolutePath());
                    return;
                case "r":
                    shuffleDatabase();
                    break;
                case "l":
                    loop = !loop;
                    break;
                case "z":
                    updateMetadata();
                    break;
                /*case "}": {
                    debug(inputScanner);
                    return;
                } //*///
                default:
                    unknownCommandError();
            }
        }

        // Modifies: this
        // Effects:  plays most recently played song, if possible
        private static void playPrevious() {
            if (played.isEmpty()) {
                AnsiConsole.out().println("No previous song to play!");
                return;
            }
            if (nowPlaying != null) {
                songQueue.addFirst(nowPlaying);
            }
            playDbFile(played.getFirst());
            played.removeFirst();
        }

        // Effects: shuffles the database
        private static void shuffleDatabase() {
            LinkedList<AudioDataStructure> databaseClone = new LinkedList<>();
            for (int i = 0; i < database.audioListSize(); i++) {
                databaseClone.addLast(database.getAudioFile(i));
            }
            Random rng = new Random();
            while (!databaseClone.isEmpty()) {
                int randNumb = (int)Math.min(Math.floor(rng.nextDouble() * databaseClone.size()),
                        databaseClone.size() - 1);
                songQueue.addLast(databaseClone.get(randNumb));
                databaseClone.remove(randNumb);
            }
            playDbFile(songQueue.getFirst());
            songQueue.removeFirst();
        }

        // Modifies: this
        // Effects:  plays next song, if possible
        private static void playNext() {
            if (songQueue.isEmpty()) {
                AnsiConsole.out().println("No song in queue to play!");
                return;
            }
            // If song queue isn't empty we're playing SOMETHING
            played.addFirst(nowPlaying);
            playDbFile(songQueue.getFirst());
            songQueue.removeFirst();
        }

        // Modifies: a lot:
        //           - filesystem
        //           - console
        //           - audio converter list
        // Effects:  Starts an audio converter thread
        private static void startEncoder(Scanner scanner) {
            AnsiConsole.out().print(Ansi.ansi().eraseScreen());
            AnsiConsole.out().print(Ansi.ansi().cursor(2, 1));
            AnsiConsole.out().println("Please enter the source filename:");
            doPlaybackStatusWrite();
            // Fix whitespace errors
            filename = scanner.nextLine().trim();
            // Check if file exists
            File f = new File(filename);
            if (f.isFile()) {
                AudioConversion converter = makeAudioConverter(new AudioDataStructure(f.getAbsolutePath()), scanner);
                if (converter.errorOccurred()) {
                    AnsiConsole.out().println("An error has occurred.");
                    return;
                }
                audioConverterList.add(converter);
                converter.start();
            } else {
                AnsiConsole.out().println("File doesn't exist, is a directory, or is inaccessible.");
            }
        }

        // Modifies: a lot:
        //           - filesystem
        //           - console
        //           - audio converter list
        // Effects:  Starts an audio converter thread using a database file
        private static void encodeDatabaseFile(AudioDataStructure audioDataStructure, Scanner scanner) {
            File f = new File(audioDataStructure.getFilename());
            if (f.isFile()) {
                AudioConversion converter = makeAudioConverter(audioDataStructure, scanner);
                if (converter.errorOccurred()) {
                    AnsiConsole.out().println("An error has occurred.");
                    return;
                }
                audioConverterList.add(converter);
                converter.start();
            } else {
                AnsiConsole.out().println("File no longer exists, or is currently inaccessible.");
                Cli.wait(1000);
            }
        }

        // Effects: returns an audio converter object with user-selected settings
        private static AudioConversion makeAudioConverter(AudioDataStructure source, Scanner scanner) {
            AnsiConsole.out().println("Please enter the target filename:");
            AudioConversion base = new AudioConversion(source, scanner.nextLine().trim());
            HashMap<String, List<String>> options = base.getOptions();
            if (options == null) {
                AnsiConsole.out().println("Encoder does not have any selectable options.");
            } else {
                HashMap<String, String> selectedOptions = new HashMap<>();
                for (Map.Entry<String, List<String>> option : options.entrySet()) {
                    AnsiConsole.out().println("Options for " + option.getKey() + ":");
                    for (int i = 0; i < option.getValue().size(); i++) {
                        AnsiConsole.out().println(i + ". " + option.getValue().get(i));
                    }
                    AnsiConsole.out().println("Please select an option.");
                    selectedOptions.put(option.getKey(),
                            option.getValue().get(getUserIntToValue(option.getValue().size(), scanner)));
                }
                base.setAudioSettings(selectedOptions);
            }
            return base;
        }

        // Effects: returns a valid number between 0 and a specified value
        private static int getUserIntToValue(int max, Scanner scanner) {
            int out;
            while (true) {
                try {
                    String read = scanner.nextLine();
                    out = Integer.parseInt(read);
                    if (out > max) {
                        throw new NumberFormatException();
                    }
                    return out;
                } catch (NumberFormatException e) {
                    AnsiConsole.out().println("Invalid number, please pick again.");
                }
            }
        }

        // Effects: wait for a set time
        private static void wait(int millis) {
            ExceptionIgnore.ignoreExc(() -> sleep(millis));
        }

        /*private static void debug(Scanner scanner) {
            AnsiConsole.out().println(Ansi.ansi().fgBrightGreen() + "Debugging now!");
            String file = scanner.nextLine().trim();
            AnsiConsole.out().println(AudioFileLoader.getAudioFiletype(file));
            wait(10000);
            AnsiConsole.out().print(Ansi.ansi().fgDefault());
        } //*/// Debugging

        // Modifies: database
        // Effects:  passes a file into the database addFileToSongDatabase() function, adding it
        private static void addDatabaseFile(Scanner scanner) {
            AnsiConsole.out().print(Ansi.ansi().eraseScreen());
            doPlaybackStatusWrite();
            AnsiConsole.out().print(Ansi.ansi().cursor(2, 1));
            AnsiConsole.out().println("Please enter the filename:");
            String filenameIn = scanner.nextLine();
            // Fix whitespace errors
            String filename = filenameIn.trim();
            // Check if file exists
            File f = new File(filename);
            if (f.isFile()) { // Database uses absolute file paths, otherwise it would fail to load audio
                database.addFileToSongDatabase(f.getAbsolutePath());
                database.sanitizeAudioDatabase();
                database.sortSongList("Default");
            } else {
                AnsiConsole.out().println("File doesn't exist, is a directory, or is inaccessible.");
                wait(1000);
            }
        }

        // Modifies: database
        // Effects:  passes a directory into the database addDirToSongDatabase() function, adding it
        private static void addDatabaseDir(Scanner scanner) {
            AnsiConsole.out().print(Ansi.ansi().eraseScreen());
            doPlaybackStatusWrite();
            AnsiConsole.out().print(Ansi.ansi().cursor(2, 1));
            AnsiConsole.out().println("Please enter the directory name:");
            String filenameIn = scanner.nextLine();
            // Fix whitespace errors
            String filename = filenameIn.trim();
            // Check if file exists
            File f = new File(filename);
            if (f.isDirectory()) { // Database uses absolute file paths, otherwise it would fail to load audio
                database.addDirToSongDatabase(f.getAbsolutePath());
                database.sanitizeAudioDatabase();
                database.sortSongList("Default");
            } else {
                AnsiConsole.out().println("Directory doesn't exist, is a file, or is inaccessible.");
                wait(1000);
            }
        }

        // Modifies: database
        // Effects:  updates all database metadata caches
        private static void updateMetadata() {
            for (int i = 0; i < database.audioListSize(); i++) {
                database.updateAudioFile(i, new AudioDataStructure(database.getAudioFile(i).getFilename()));
            }
        }

        // Effects: Prints the error for an unknown command
        private static void unknownCommandError() {
            AnsiConsole.out().println("Invalid option.");
            AnsiConsole.out().println("Either you mistyped something, or it hasn't been implemented yet.");
            wait(1000);
        }

        // Modifies: this, playbackManager
        // Effects:  Cleans up extra threads
        private static void cleanup() {
            end = true;
            visualizerThread.killThread();
            visualizerThread.interrupt();
            playbackManager.cleanBackend();
            visualizerThread = null;
            playbackManager = null;
        }

        // Modifies: playbackManager and its decoding thread
        // Effects: plays a file
        private static void playFile(Scanner inputScanner) {
            AnsiConsole.out().print(Ansi.ansi().eraseScreen());
            AnsiConsole.out().print(Ansi.ansi().cursor(2, 1));
            AnsiConsole.out().println("Please enter the filename:");
            doPlaybackStatusWrite();
            // Fix whitespace errors
            filename = inputScanner.nextLine().trim();
            // Check if file exists
            File f = new File(filename);
            if (f.isFile()) {
                visualizerThread.killThread();
                visualizerThread.safeJoin();
                nowPlaying = new AudioDataStructure(f.getAbsolutePath());
                playbackManager.loadAudio(f.getAbsolutePath(), nowPlaying);
                playbackManager.startAudioDecoderThread();
                playbackManager.playAudio();
                id3 = playbackManager.getID3();
                visualizerThread = new PlaybackThread();
                visualizerThread.start();
            } else {
                AnsiConsole.out().println("File doesn't exist, is a directory, or is inaccessible.");
                wait(1000);
            }
        }

        // Effects: runs writePlaybackState() will null catching
        //          also doubles as checking against IntelliJ console
        private static void doPlaybackStatusWrite() {
            if (AnsiConsole.getTerminalWidth() == 0) {
                String w = formatTime((long) floor(playbackManager.getCurrentTime())) + " of "
                        + formatTime((long) floor(playbackManager.getFileDuration()));
                if (playbackManager.paused()) {
                    AnsiConsole.out().print(Ansi.ansi().fgBrightRed().toString() + "PAUSED"
                            + Ansi.ansi().fgDefault().toString() + "; ");
                }
                AnsiConsole.out().println(w);
                return;
            }

            ExceptionIgnore.ignoreExc(() -> writePlaybackState());
        }

        // Modifies: Console
        // Effects:  updates the audio status bar
        //           writes the entire status bar in one single print statement,
        //           which fixes a race condition with the KEYBOARD
        private static void writePlaybackState() {
            double time = playbackManager.getPercentPlayed();
            int w = AnsiConsole.getTerminalWidth() - 2;
            String color = playbackManager.audioIsSkipping() ? Ansi.ansi().fgBrightBlack().toString()
                    : Ansi.ansi().fgBrightMagenta().toString();
            String burstWrite = formatTime((long) floor(playbackManager.getCurrentTime())) + " [";
            if (playbackManager.paused()) {
                burstWrite = Ansi.ansi().fgBrightRed().toString() + "PAUSED" + color + " " + burstWrite;
                w += Ansi.ansi().fgBrightRed().toString().length() + Ansi.ansi().fgBrightMagenta().toString().length();
            }
            String fileDuration = formatTime((long) floor(playbackManager.getFileDuration()));
            w -= burstWrite.length() + fileDuration.length() + (loop ? " LOOP".length() : 0);
            burstWrite += getPlaybackBar(time, w);
            AnsiConsole.out().print(Ansi.ansi().saveCursorPosition().toString() + color
                    + Ansi.ansi().cursor(1, 1).toString() + burstWrite + "] " + fileDuration
                    + (loop ? " LOOP" : "") + Ansi.ansi().restoreCursorPosition().toString()
                    + Ansi.ansi().fgDefault().toString());
        }

        // Effects: gets the playback bar for writePlaybackState()
        private static String getPlaybackBar(double time, int w) {
            StringBuilder burstWrite = new StringBuilder();
            for (int i = 0; i < w; i++) {
                if (i < floor(time * w)) {
                    burstWrite.append("#");
                } else if (i < Math.round(time * w)) {
                    burstWrite.append("=");
                } else {
                    burstWrite.append("-");
                }
            }
            return burstWrite.toString();
        }

        // Modifies: loaded file container through playbackManager
        // Effects:  seeks to location in song
        private static void seekAudio(Scanner inputScanner) {
            AnsiConsole.out().print(Ansi.ansi().eraseScreen());
            doPlaybackStatusWrite();
            AnsiConsole.out().print(Ansi.ansi().cursor(2, 1));
            AnsiConsole.out().println("Please input the time (in seconds) you'd like to go to.");
            while (true) {
                try {
                    playbackManager.seekTo(inputScanner.nextDouble());
                    inputScanner.nextLine(); // Bugfix (fixes duplicate menu)
                    return;
                } catch (InputMismatchException e) {
                    if (inputScanner.nextLine().equalsIgnoreCase("q")) {
                        return;
                    }
                    AnsiConsole.out().print(Ansi.ansi().cursorUpLine());
                    AnsiConsole.out().println("That's not a time; enter \"Q\" to leave this menu.");
                }
            }
        }

        // Effects: prints main menu
        private static void printMenu() {
            AnsiConsole.out().print(Ansi.ansi().eraseScreen());
            doPlaybackStatusWrite();
            printMenuBlock();
            if (playbackManager.audioIsLoaded()) {
                printLoadedAudioStatus();
            }
            if (!audioConverterList.isEmpty()) {
                printReencodeStatus();
            }
            if (!played.isEmpty() || !songQueue.isEmpty()) {
                horizonalBar();
            }
            if (!played.isEmpty()) {
                AnsiConsole.out().println("<. Play previous song");
            }
            if (!songQueue.isEmpty()) {
                AnsiConsole.out().println(">. Skip to next song");
            }
        }

        // Effects: prints main menu static text
        private static void printMenuBlock() {
            AnsiConsole.out().print(Ansi.ansi().cursor(2, 1));
            AnsiConsole.out().println("What would you like to do?");
            AnsiConsole.out().println("1. Play a file");
            AnsiConsole.out().println("2. Add file to database");
            AnsiConsole.out().println("3. Scan directory into database");
            AnsiConsole.out().println("4. Browse database");
            AnsiConsole.out().println("5. Save database");
            AnsiConsole.out().println("6. Re-encode file");
            AnsiConsole.out().println("9. List all files in database");
            AnsiConsole.out().println("R. Shuffle and play database");
            AnsiConsole.out().println("Z. Reload database metadata");
            AnsiConsole.out().println("L. Toggle loop");
            AnsiConsole.out().println("Q. Exit");
            AnsiConsole.out().println("M. Enter database backup manager");
        }

        // Modifies: console
        // Effects:  prints the now playing text and menu
        private static void printLoadedAudioStatus() {
            horizonalBar();
            AnsiConsole.out().println(Ansi.ansi().fgBrightCyan().toString() + "Now Playing: "
                    + playbackManager.getPlaybackString() + Ansi.ansi().fgDefault().toString());
            AnsiConsole.out().println("P. Pause");
            AnsiConsole.out().println("C. Play");
            AnsiConsole.out().println("S. Seek");
            AnsiConsole.out().println("D. Add playing file to database");
        }

        // Modifies: console
        // Effects:  prints the status of encoders
        private static void printReencodeStatus() {
            horizonalBar();
            AnsiConsole.out().println(Ansi.ansi().fgBrightYellow().toString() + "Converting "
                    + activeAudioConversions() + " audio files..." + Ansi.ansi().fgDefault().toString());
            int dead = deadAudioConversions();
            if (dead != 0) {
                AnsiConsole.out().println(Ansi.ansi().fgBrightRed().toString() + dead
                        + " audio conversions failed." + Ansi.ansi().fgDefault().toString());
            }
        }

        private static int songID = 0;

        // Modifies: this
        // Effects:  browses menu (duh)
        private static void browseMenu(Scanner inputScanner) {
            if (database.audioListSize() == 0) {
                return;
            }
            state = MenuState.CLI_BROWSEMENU;
            while (true) {
                if (!songQueue.isEmpty() && !playbackManager.audioIsLoaded()) {
                    playDbFile(songQueue.getFirst());
                    songQueue.removeFirst();
                }
                if (songID < 0) {
                    songID += database.audioListSize();
                }
                if (songID >= database.audioListSize()) {
                    songID -= database.audioListSize();
                }
                printBrowseMenu();
                int l = browseSwitch(inputScanner.nextLine(), songID, inputScanner);
                if (l == 7000) {
                    return;
                }
                songID += l;
            }
        }

        @SuppressWarnings("methodlength") // Large switch/case
        // Modifies: this
        // Effects:  handles the switch case for browseMenu()
        private static int browseSwitch(String in, int idx, Scanner scanner) {
            switch (in.toLowerCase()) {
                case "1":
                    return -1;
                case "2":
                    return 1;
                case "p":
                    playDbFile(database.getAudioFile(idx));
                    break;
                case "c":
                    playDbFile(database.getAudioFile(idx));
                case "q":
                    return 7000;
                case "r":
                    togglePlayback();
                    break;
                case "l":
                    songQueue.add(database.getAudioFile(idx));
                    break;
                case "e":
                    encodeDatabaseFile(database.getAudioFile(idx), scanner);
                    break;
            }
            return 0;
        }

        // Modifies: playbackManager and its decoding thread
        // Effects:  plays a file from database
        private static void playDbFile(AudioDataStructure audioDataStructure) {
            File f = new File(audioDataStructure.getFilename());
            if (f.isFile()) {
                visualizerThread.killThread();
                visualizerThread.safeJoin();
                playbackManager.loadAudio(f.getAbsolutePath(), nowPlaying);
                nowPlaying = audioDataStructure;
                playbackManager.startAudioDecoderThread();
                playbackManager.playAudio();
                id3 = playbackManager.getID3();
                audioDataStructure.updateID3(id3); // Update on file load
                visualizerThread = new PlaybackThread();
                visualizerThread.start();
            } else {
                AnsiConsole.out().println("File no longer exists, or is currently inaccessible.");
                wait(1000);
            }
        }

        // Effects: prints browse menu
        private static void printBrowseMenu() {
            AnsiConsole.out().print(Ansi.ansi().eraseScreen());
            doPlaybackStatusWrite(); // Checkstyle made me do this
            String playMessage = playbackManager.paused() ? "R. Resume song" : "R. Pause song";
            AnsiConsole.out().print(Ansi.ansi().cursor(2, 1));
            if (playbackManager.audioIsLoaded()) {
                AnsiConsole.out().println(Ansi.ansi().fgBrightCyan().toString() + "Now Playing: "
                        + playbackManager.getPlaybackString() + Ansi.ansi().fgDefault().toString());
                horizonalBar();
            }
            AnsiConsole.out().println(Ansi.ansi().fgBrightGreen().toString()
                    + database.getAudioFile(songID).getPlaybackString() + Ansi.ansi().fgDefault().toString());
            horizonalBar();
            AnsiConsole.out().println("What would you like to do?");
            AnsiConsole.out().println("1. Browse to previous song in database");
            AnsiConsole.out().println("2. Browse to next song in database");
            AnsiConsole.out().println("E. Re-encode song");
            printIfAudioLoaded(playMessage + "\n");
            AnsiConsole.out().println("P. Play song");
            printIfAudioLoaded("L. Queue song\n");
            AnsiConsole.out().println("C. Play song and return to main menu");
            AnsiConsole.out().println("Q. Return to main menu");
        }

        // Modifies: console
        // Effects:  prints string if audio is loaded
        //           function name explains itself really
        private static void printIfAudioLoaded(String str) {
            if (playbackManager.audioIsLoaded()) {
                AnsiConsole.out().print(str);
            }
        }

        // Effects: writes bar across the screen
        private static void horizonalBar() {
            for (int i = 1; i < AnsiConsole.getTerminalWidth(); i++) { // Skip first char
                AnsiConsole.out().print("-");
            }
            AnsiConsole.out().println("-"); // Final in line
        }

        // Modifies: database (maybe)
        // Effects:  at minimum, prints the menu to the screen
        //           at maximum, resets the database
        private static void dbMenu(Scanner inputScanner) {
            state = MenuState.CLI_OTHER;
            printDbMenu();
            String selected = inputScanner.nextLine();
            switch (selected.toLowerCase()) {
                case "1":
                    database.revertDb();
                    break;
                case "2":
                    DataManager.cleanDb(inputScanner.nextLine().trim());
                    break;
                case "3":
                    database.cleanOldDb();
                    break;
                case "r":
                    database.cleanDbFldr();
                    break;
            }
            AnsiConsole.out().print(Ansi.ansi().fgDefault());
        }

        // Effects: prints database management menu
        private static void printDbMenu() {
            AnsiConsole.out().print(Ansi.ansi().eraseScreen());
            doPlaybackStatusWrite();
            AnsiConsole.out().print(Ansi.ansi().fgBrightRed());
            AnsiConsole.out().print(Ansi.ansi().cursor(2, 1));
            AnsiConsole.out().println("Here be dragons...");
            AnsiConsole.out().println("You have been warned.");
            AnsiConsole.out().println("1. Revert to previous database version");
            AnsiConsole.out().println("2. Clean database files (enter *.audiodex.basedb file on next line)");
            AnsiConsole.out().println("3. Clean all old database files");
            AnsiConsole.out().println("R. Refresh database folder");
            AnsiConsole.out().println("To return to main menu, enter any other character");
        }
    }
}