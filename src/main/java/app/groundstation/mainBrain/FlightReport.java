package app.groundstation.mainBrain;

import app.groundstation.map.WorldPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Accumulates flight telemetry and events for the Reports section.
 */
public class FlightReport {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String flightId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String missionStatus = "NOT STARTED";
    private String flightStatus = "IDLE";
    private int pathPointCount;
    private double maxAltitude;
    private int minBattery = 100;
    private final List<String> events = new ArrayList<>();

    public FlightReport() {
        this.flightId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public void startNewFlight() {
        startTime = LocalDateTime.now();
        endTime = null;
        missionStatus = "NOT STARTED";
        flightStatus = "PREPARING";
        pathPointCount = 0;
        maxAltitude = 0;
        minBattery = 100;
        events.clear();
        recordEvent("New flight started");
    }

    public void endFlight(String reason) {
        endTime = LocalDateTime.now();
        flightStatus = reason;
        recordEvent("Flight ended: " + reason);
    }

    public void recordEvent(String event) {
        events.add(FMT.format(LocalDateTime.now()) + " — " + event);
    }

    public void updateFromState(DroneState state) {
        pathPointCount = state.getPathPoints().size();
        if (state.hasPosition()) {
            maxAltitude = Math.max(maxAltitude, state.getCurrentAlt());
        }
        int battery = state.getCurrentBattery();
        if (battery >= 0) {
            minBattery = Math.min(minBattery, battery);
        }
    }

    public void setMissionStatus(String status) {
        this.missionStatus = status;
        recordEvent("Mission: " + status);
    }

    public void setFlightStatus(String status) {
        this.flightStatus = status;
    }

    public String getFlightId() { return flightId; }
    public String getMissionStatus() { return missionStatus; }
    public String getFlightStatus() { return flightStatus; }
    public int getPathPointCount() { return pathPointCount; }
    public double getMaxAltitude() { return maxAltitude; }
    public int getMinBattery() { return minBattery == 100 ? -1 : minBattery; }

    public String getStartTimeDisplay() {
        return startTime != null ? FMT.format(startTime) : "---";
    }

    public String getEndTimeDisplay() {
        return endTime != null ? FMT.format(endTime) : "---";
    }

    public String getDurationDisplay() {
        if (startTime == null) return "---";
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        long seconds = java.time.Duration.between(startTime, end).getSeconds();
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    public List<String> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public void exportCsv(Path file, DroneState state) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("flight_id,").append(flightId).append('\n');
        sb.append("start_time,").append(getStartTimeDisplay()).append('\n');
        sb.append("end_time,").append(getEndTimeDisplay()).append('\n');
        sb.append("duration,").append(getDurationDisplay()).append('\n');
        sb.append("mission_status,").append(missionStatus).append('\n');
        sb.append("flight_status,").append(flightStatus).append('\n');
        sb.append("max_altitude_m,").append(String.format("%.1f", maxAltitude)).append('\n');
        sb.append("min_battery_pct,").append(minBattery).append('\n');
        sb.append("path_points,").append(pathPointCount).append('\n');
        sb.append('\n').append("event_log\n");
        for (String e : events) {
            sb.append(e).append('\n');
        }
        sb.append('\n').append("path_lat,path_lon\n");
        for (WorldPoint p : state.getPathPoints()) {
            sb.append(p.getX()).append(',').append(p.getY()).append('\n');
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }
}
