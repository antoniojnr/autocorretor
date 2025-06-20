package main;

public class Utils {
    public static String color(String text, Color color) {
        switch (color) {
            case RED: return "\u001B[31m" + text + "\u001B[0m";
            case BLUE: return "\u001B[34m" + text + "\u001B[0m";
            case GREEN: return "\u001B[32m" + text + "\u001B[0m";
            case CYAN: return "\u001B[36m" + text + "\u001B[0m";
            case PURPLE: return "\u001B[35m" + text + "\u001B[0m";
            case YELLOW: return "\u001B[33m" + text + "\u001B[0m";
            default: return text;
        }
    }

    public static void printColored(String text, Color color) {
        System.out.println(color(text, color));
    }

    public static void printError(String text) {
        printColored(text, Color.RED);
    }

    public static void printInfo(String text) {
        printColored(text, Color.CYAN);
    }

    public static void printSuccess(String text) {
        printColored(text, Color.GREEN);
    }
}
