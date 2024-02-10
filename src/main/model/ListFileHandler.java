package model;

import audio.AudioDataStructure;
import audio.AudioFileType;
import ui.Main;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

// Static class to handle the file list
public class ListFileHandler {
    public static final String[] RESERVED_CHARACTERS = {
            "🜅", // Sublist file declarator
            "🜇"  // Element separator
    };

    // Effects: takes in a file and decodes the file, going through the tree
    public static AudioDataStructure[] decodeList(String filename) {
        String main = null;
        try {
            main = new String(Files.readAllBytes(Paths.get(filename)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new AudioDataStructure[]{};
        }
        List<AudioDataStructure> out = new ArrayList<AudioDataStructure>();
        String[] keys = main.split(RESERVED_CHARACTERS[1]); // Element separator
        for (String data : keys) {
            if (data.startsWith(RESERVED_CHARACTERS[0])) {
                Main.CliInterface.println("Loaded database file " + filename + "!\nLoading database file "
                        + data.substring(RESERVED_CHARACTERS[0].length()) + "...");
                Collections.addAll(out, decodeList(data.substring(RESERVED_CHARACTERS[0].length())));
                return out.toArray(new AudioDataStructure[0]);
            } else { // This is now a normal file index
                if (data.equals("")) {
                    continue;
                }
                out.add(AudioDataStructure.fromString(data));
            }
        }
        Main.CliInterface.println("Loaded the final database file, " + filename + "!");
        return out.toArray(new AudioDataStructure[0]);
    }

    // Modifies: database files
    // Effects:  encodes audio data list to file
    //           can throw an IOException
    public static void encodeList(AudioDataStructure[] data, String filename) throws IOException {
        List<AudioDataStructure> lol = new ArrayList<AudioDataStructure>();
        for (AudioDataStructure struc : data) {
            lol.add(struc);
        }
        encodeList(lol, filename);
    }

    // Modifies: database files
    // Effects:  encodes audio data list to file
    //           can throw an IOException
    public static void encodeList(List<AudioDataStructure> data, String filename) throws IOException {
        String out = "";
        while (!data.isEmpty()) {
            String str = data.get(0).toString();
            if (out.length() + str.length() > 65536 && str.length() < 65536) {
                File f = new File(filename);
                String dir = f.toPath().toAbsolutePath().getParent().toString() + "/";
                String newFileName;
                do {
                    newFileName = dir + getSaltString() + "." + getSaltString() + ".audioDex.db";
                } while ((new File(newFileName)).exists());
                // This used to set to f but then checkstyle got cranky ab function length
                out += RESERVED_CHARACTERS[0] + newFileName;
                FileManager.writeToFile(filename, out);
                Main.CliInterface.println("Saved database file " + filename + "!");
                Main.CliInterface.println("Saving database file " + newFileName + "...");
                encodeList(data, newFileName);
                return;
            }
            data.remove(0); // Pop first element
            out += str + RESERVED_CHARACTERS[1];
        }
        FileManager.writeToFile(filename, out.substring(0,
                Math.max(0, out.length() - RESERVED_CHARACTERS[1].length())));
        Main.CliInterface.println("Saved final database file, " + filename + "!");
    }

    // Effects: creates random string for database files
    private static String getSaltString() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
        StringBuilder salt = new StringBuilder();
        Random rnd = new Random();
        while (salt.length() < 10) {
            int index = (int) (rnd.nextFloat() * chars.length());
            salt.append(chars.charAt(index));
        }
        return salt.toString();
    }
}
