package app.groundstation.serialCommumication.DataHandlers;

/**
 * Parsed telemetry frame from the drone.
 * Protocol (text, newline-terminated): POS,lat,lon,alt[,battery]
 * battery is -1 when the drone does not transmit it.
 */
public record PositionUpdate(double lat, double lon, double alt, int battery) {

    /** Convenience factory when battery percentage is not in the frame. */
    public static PositionUpdate withoutBattery(double lat, double lon, double alt) {
        return new PositionUpdate(lat, lon, alt, -1);
    }
}
