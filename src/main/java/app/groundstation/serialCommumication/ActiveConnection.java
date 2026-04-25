package app.groundstation.serialCommumication;

import com.fazecast.jSerialComm.SerialPort;
import com.gluonhq.maps.MapPoint;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Manages the serial session after a successful handshake.
 * Provides high-level send methods (text command, mission area).
 */
public class ActiveConnection {

    private final int sessionID;
    private final SerialPort port;
    private boolean active;

    public ActiveConnection(int sessionID, SerialPort port) {
        this.sessionID = sessionID;
        this.port = port;
        this.active = true;
    }

    /**
     * Send a raw newline-terminated text command over the serial port.
     * Thread-safe (jSerialComm write is synchronized internally).
     */
    public boolean sendCommand(String command) {
        if (!active || port == null || !port.isOpen()) return false;
        byte[] bytes = (command + "\n").getBytes(StandardCharsets.UTF_8);
        int written = port.writeBytes(bytes, bytes.length);
        return written == bytes.length;
    }

    /**
     * Send mission search area as: MISSION,<sessionID>,lat1,lon1,...,lat4,lon4
     * Points must contain exactly 4 MapPoints (the polygon corners).
     */
    public boolean sendMissionArea(List<MapPoint> points) {
        if (points == null || points.size() < 4) return false;
        StringBuilder sb = new StringBuilder("MISSION,").append(sessionID);
        for (int i = 0; i < 4; i++) {
            MapPoint p = points.get(i);
            sb.append(String.format(",%.7f,%.7f", p.getLatitude(), p.getLongitude()));
        }
        return sendCommand(sb.toString());
    }

    /** Send a GET_STATUS ping to the drone. */
    public boolean requestStatus() {
        return sendCommand("GET_STATUS," + sessionID);
    }

    public boolean isActive() {
        return active && port != null && port.isOpen();
    }

    public int getSessionID() {
        return sessionID;
    }

    /** Close the serial port and mark this connection inactive. */
    public void close() {
        active = false;
        if (port != null && port.isOpen()) {
            port.closePort();
        }
    }
}
