package app.groundstation.serialCommumication.DataHandlers.DataFormats;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A command sent from the Ground Control Station to the drone.
 *
 * Supported commands:
 *   MISSION_AREA  — transmit the 4-corner search polygon
 *   START_MISSION — begin the autonomous search run
 *   ABORT         — halt all autonomous activity immediately
 *   GET_STATUS    — request a one-shot status response
 *   RTL           — return to launch point
 *   HOLD          — pause autonomous movement
 *   RESUME        — resume autonomous movement
 *   LAND          — initiate landing sequence
 */
public class DroneRequest {

    public enum CommandType {
        MISSION_AREA,
        START_MISSION,
        ABORT,
        GET_STATUS,
        RTL,
        HOLD,
        RESUME,
        LAND
    }

    /** lat/lon pair representing one polygon vertex or waypoint. */
    public static class Waypoint {
        public final double lat;
        public final double lon;

        public Waypoint(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }
    }

    private String sessionID;
    private CommandType commandType;
    private final List<Waypoint> waypoints = new ArrayList<>();

    public DroneRequest() {}

    public DroneRequest(String sessionID, CommandType commandType) {
        this.sessionID = sessionID;
        this.commandType = commandType;
    }

    public void addWaypoint(double lat, double lon) {
        waypoints.add(new Waypoint(lat, lon));
    }

    public List<Waypoint> getWaypoints() {
        return Collections.unmodifiableList(waypoints);
    }

    public String getSessionID() { return sessionID; }
    public void setSessionID(String sessionID) { this.sessionID = sessionID; }

    public CommandType getCommandType() { return commandType; }
    public void setCommandType(CommandType commandType) { this.commandType = commandType; }

    /** Serialize to JSON for network or JSON-over-serial use. */
    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        obj.put("sessionID", sessionID);
        obj.put("command", commandType != null ? commandType.name() : "UNKNOWN");
        JSONArray pts = new JSONArray();
        for (Waypoint w : waypoints) {
            JSONObject pt = new JSONObject();
            pt.put("lat", w.lat);
            pt.put("lon", w.lon);
            pts.put(pt);
        }
        obj.put("waypoints", pts);
        return obj;
    }

    /** Serialize to the plain-text serial protocol understood by the drone firmware. */
    public String toSerialCommand() {
        StringBuilder sb = new StringBuilder(commandType.name()).append(",").append(sessionID);
        for (Waypoint w : waypoints) {
            sb.append(String.format(",%.7f,%.7f", w.lat, w.lon));
        }
        return sb.toString();
    }
}
