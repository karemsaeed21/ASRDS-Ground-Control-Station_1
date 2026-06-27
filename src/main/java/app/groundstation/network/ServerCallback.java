package app.groundstation.network;

import org.json.JSONObject;

public interface ServerCallback {
    void onDroneConnected();
    void onDroneDisconnected();
    void onSearchAreaRequested();
    void onStatusReceived(double x, double y, double z, double headingRad, String state);
    void onHumanDetected(String messageId, double hX, double hY, double confidence);
    void onSearchComplete();
}
