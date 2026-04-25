package app.groundstation.mainBrain;

import app.groundstation.serialCommumication.DataHandlers.PositionUpdate;
import app.groundstation.map.MapObject;
import javafx.application.Platform;

import java.util.function.Consumer;

/**
 * Coordinates map and drone state. Receives position updates from the drone,
 * refreshes the map on the JavaFX thread, and notifies a telemetry listener
 * so the UI can display live lat/lon/alt/battery without coupling to Brain internals.
 */
public class Brain {

    private final MapObject mapObject;
    private final DroneState droneState = new DroneState();
    private Consumer<DroneState> telemetryListener;

    public Brain(MapObject mapObject) {
        this.mapObject = mapObject;
    }

    /**
     * Register a callback invoked on the JavaFX thread after every position update.
     * Use this to push live telemetry values into the UI.
     */
    public void setTelemetryListener(Consumer<DroneState> listener) {
        this.telemetryListener = listener;
    }

    /**
     * Called when a position frame is parsed from serial. Safe to call from any thread.
     */
    public void onPositionReceived(PositionUpdate position) {
        double lat = position.lat();
        double lon = position.lon();
        double alt = position.alt();
        int battery = position.battery();
        droneState.setPosition(lat, lon, alt);
        droneState.addPathPoint(lat, lon);
        if (battery >= 0) {
            droneState.setBattery(battery);
        }
        Platform.runLater(() -> {
            mapObject.updateDronePosition(lat, lon);
            mapObject.updateDronePath(droneState.getPathPoints());
            if (telemetryListener != null) {
                telemetryListener.accept(droneState);
            }
        });
    }

    /**
     * Clear drone state and hide marker/path. Call from JavaFX thread.
     */
    public void clearDronePreview() {
        droneState.clear();
        mapObject.clearDronePreview();
        if (telemetryListener != null) {
            telemetryListener.accept(droneState);
        }
    }

    public DroneState getDroneState() {
        return droneState;
    }
}
