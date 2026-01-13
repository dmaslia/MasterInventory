package com.danielmaslia.masterinventory;

public class StringUtils {

    public static String formatEnumString(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String[] words = input.split("_");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.isEmpty()) continue;

            String formattedWord = word.charAt(0) + word.substring(1).toLowerCase();
            result.append(formattedWord).append(" ");
        }

        return result.toString().trim();
    }
}
