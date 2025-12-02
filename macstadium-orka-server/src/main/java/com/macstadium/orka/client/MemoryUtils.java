package com.macstadium.orka.client;

/**
 * Utility class for parsing memory strings from Orka API.
 */
public final class MemoryUtils {

    private MemoryUtils() {
        // Utility class
    }

    /**
     * Parses memory string to float in GB.
     * Handles formats like "128.00G", "28.00G", "30", "30G".
     *
     * @param memoryStr Memory string from Orka API
     * @return Memory in GB as float, or 0 if parsing fails
     */
    public static float parseMemoryToGb(String memoryStr) {
        if (memoryStr == null || memoryStr.isEmpty()) {
            return 0;
        }
        String numStr = memoryStr.replaceAll("[^0-9.]", "");
        try {
            return Float.parseFloat(numStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

