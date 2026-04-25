package app.groundstation.serialCommumication.DataHandlers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses position messages from serial stream.
 * Expects lines: POS,lat,lon,alt[,battery%] (e.g. "POS,31.19342,29.90032,100.5,87")
 * The battery field is optional; -1 is used when absent.
 */
public class PositionParser {

    private final StringBuilder buffer = new StringBuilder();

    /**
     * Append received bytes and parse any complete lines.
     * @param data raw bytes from serial
     * @return list of parsed position updates (may be empty)
     */
    public List<PositionUpdate> parse(byte[] data) {
        if (data == null || data.length == 0) {
            return List.of();
        }
        buffer.append(new String(data, StandardCharsets.UTF_8));
        List<PositionUpdate> results = new ArrayList<>();
        int idx;
        while ((idx = buffer.indexOf("\n")) >= 0) {
            String line = buffer.substring(0, idx).trim();
            buffer.delete(0, idx + 1);
            PositionUpdate pos = parseLine(line);
            if (pos != null) {
                results.add(pos);
            }
        }
        return results;
    }

    private static PositionUpdate parseLine(String line) {
        if (line == null || !line.startsWith("POS,")) {
            return null;
        }
        String[] parts = line.substring(4).split(",");
        if (parts.length < 3) {
            return null;
        }
        try {
            double lat = Double.parseDouble(parts[0].trim());
            double lon = Double.parseDouble(parts[1].trim());
            double alt = Double.parseDouble(parts[2].trim());
            int battery = parts.length >= 4 ? Integer.parseInt(parts[3].trim()) : -1;
            return new PositionUpdate(lat, lon, alt, battery);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void clear() {
        buffer.setLength(0);
    }
}
