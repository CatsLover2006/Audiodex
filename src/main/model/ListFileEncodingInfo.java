package model;

public class ListFileEncodingInfo {
    public static final String[] RESERVED_CHARACTERS = {
            "\uD83D\uDF01", // Key separator
            "\uD83D\uDF03", // Value signal
            "\uD83D\uDF05", // Sublist file declarator
            "\uD83D\uDF07"  // Element separator
    };
}
