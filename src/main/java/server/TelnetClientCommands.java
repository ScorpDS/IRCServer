package server;

public class TelnetClientCommands {

    public static final String ESC = "\u001B";
    public static final String SAVE_CURSOR_POS = ESC + "[s";
    public static final String LOAD_SAVED_CURSOR_POS = ESC + "[u";
    public static final String CURSOR_HOME = ESC + "[H";
    public static final String CLEAR_LINE = ESC + "[2K";
    public static final String CLEAR_SCREEN = ESC + "[2J";
    public static final String MOVE_TO_LINE_AND_COL = ESC + "[%d;%dH";
    public static final String SET_ITALIC_MODE = ESC + "[3m";
    public static final String RESET_ITALIC_MODE = ESC+ "[23m";
}
