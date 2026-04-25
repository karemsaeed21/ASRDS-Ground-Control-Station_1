package app.groundstation;

import app.groundstation.mainBrain.Brain;
import app.groundstation.mainBrain.DroneState;
import app.groundstation.map.MapObject;
import app.groundstation.map.MapPolygon;
import app.groundstation.map.PolygonPoint;
import app.groundstation.serialCommumication.ActiveConnection;
import app.groundstation.serialCommumication.DataHandlers.PositionUpdate;
import app.groundstation.serialCommumication.DroneTelemetryReader;
import app.groundstation.serialCommumication.HandShake;
import app.groundstation.serialCommumication.HandShakeResult;
import com.gluonhq.maps.MapPoint;
import com.gluonhq.maps.MapView;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.util.Duration;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class HelloController implements Initializable {

    // ── Coordinate inputs ──────────────────────────────────────────────────────
    @FXML private TextField x1_lat;
    @FXML private TextField x2_lat;
    @FXML private TextField x3_lat;
    @FXML private TextField x4_lat;
    @FXML private TextField x1_long;
    @FXML private TextField x2_long;
    @FXML private TextField x3_long;
    @FXML private TextField x4_long;

    // ── Listen / polygon buttons ───────────────────────────────────────────────
    @FXML private Button x1_listen_button;
    @FXML private Button x2_listen_button;
    @FXML private Button x3_listen_button;
    @FXML private Button x4_listen_button;
    @FXML private Button DrowButton;
    @FXML private Button ClearButton;

    // ── Connection controls ────────────────────────────────────────────────────
    @FXML private Label  droneStatusLabel;
    @FXML private Button droneConnectButton;
    @FXML private Button droneSimulateButton;

    // ── Map ───────────────────────────────────────────────────────────────────
    @FXML private AnchorPane MapContainer;

    // ── Telemetry readouts (right panel) ──────────────────────────────────────
    @FXML private Label latValueLabel;
    @FXML private Label lonValueLabel;
    @FXML private Label altValueLabel;
    @FXML private Label batteryValueLabel;
    @FXML private ProgressBar batteryBar;
    @FXML private Label pathPointsLabel;

    // ── Mission controls (right panel) ────────────────────────────────────────
    @FXML private Label  missionStatusLabel;
    @FXML private Button sendMissionButton;
    @FXML private Button clearPathButton;

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final String DRONE_ID       = "RPI001";
    private static final double SIM_LAT        = 31.19342;
    private static final double SIM_LON        = 29.90032;
    private static final double SIM_ALT        = 100.0;
    private static final double SIM_RADIUS_DEG = 0.002;

    // ── State ─────────────────────────────────────────────────────────────────
    private MapObject           mapObject;
    private Brain               brain;
    private ActiveConnection    activeConnection;
    private DroneTelemetryReader telemetryReader;
    private HandShakeResult     handshakeResult;
    private volatile boolean    droneConnected;
    private Timeline            simTimeline;
    private volatile boolean    simRunning;

    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        MapView mapView = new MapView();
        MapPolygon polygon = buildPolygon();
        mapObject = new MapObject(mapView, polygon, DrowButton, ClearButton);

        AnchorPane.setTopAnchor(mapView, 0.0);
        AnchorPane.setBottomAnchor(mapView, 0.0);
        AnchorPane.setLeftAnchor(mapView, 0.0);
        AnchorPane.setRightAnchor(mapView, 0.0);
        MapContainer.getChildren().add(mapView);

        brain = new Brain(mapObject);
        brain.setTelemetryListener(this::onTelemetryUpdate);

        resetTelemetryDisplay();
    }

    // ── Connection ────────────────────────────────────────────────────────────

    @FXML
    public void toggleDroneConnection() {
        if (droneConnected) disconnectDrone();
        else                connectDrone();
    }

    private void connectDrone() {
        setStatus("Connecting...", "#555555");
        if (droneConnectButton != null) droneConnectButton.setDisable(true);

        Task<HandShakeResult> task = new Task<>() {
            @Override
            protected HandShakeResult call() throws Exception {
                return new HandShake().makeHandShake(DRONE_ID);
            }
        };

        task.setOnSucceeded(e -> {
            HandShakeResult result = task.getValue();
            if (result != null && result.isHandShakeSuccess()) {
                handshakeResult    = result;
                activeConnection   = new ActiveConnection(result.getSessionID(), result.getPort());
                telemetryReader    = new DroneTelemetryReader(result.getPort(), brain::onPositionReceived);
                telemetryReader.start();
                droneConnected = true;
                setStatus("Connected", "#2e7d32");
                if (droneConnectButton != null) droneConnectButton.setText("Disconnect");
                if (sendMissionButton  != null) sendMissionButton.setDisable(false);
            } else {
                setStatus("Disconnected", "#8B0000");
            }
            if (droneConnectButton != null) droneConnectButton.setDisable(false);
        });

        task.setOnFailed(e -> {
            setStatus("Disconnected", "#8B0000");
            if (droneConnectButton != null) droneConnectButton.setDisable(false);
        });

        new Thread(task).start();
    }

    private void disconnectDrone() {
        if (telemetryReader != null) { telemetryReader.stop(); telemetryReader = null; }
        if (activeConnection != null) { activeConnection.close(); activeConnection = null; }
        handshakeResult = null;
        droneConnected  = false;
        Platform.runLater(() -> brain.clearDronePreview());
        setStatus("Disconnected", "#8B0000");
        if (droneConnectButton  != null) droneConnectButton.setText("Connect");
        if (sendMissionButton   != null) sendMissionButton.setDisable(true);
        if (missionStatusLabel  != null) missionStatusLabel.setText("NOT SENT");
        resetTelemetryDisplay();
    }

    // ── Simulation ────────────────────────────────────────────────────────────

    @FXML
    public void toggleDroneSimulate() {
        if (simRunning) {
            stopSimulation();
        } else {
            startSimulation();
        }
    }

    private void startSimulation() {
        simRunning = true;
        setStatus("Simulating", "#e65100");
        if (droneSimulateButton != null) droneSimulateButton.setText("Stop Simulate");
        if (sendMissionButton   != null) sendMissionButton.setDisable(false);

        final long startNanos = System.nanoTime();
        simTimeline = new Timeline(new KeyFrame(Duration.millis(200), e -> {
            double t      = (System.nanoTime() - startNanos) / 1e9;
            double lat    = SIM_LAT + SIM_RADIUS_DEG * Math.sin(t * 0.5);
            double lon    = SIM_LON + SIM_RADIUS_DEG * Math.cos(t * 0.5);
            double alt    = SIM_ALT + 5.0 * Math.sin(t * 0.3);
            int    battery = Math.max(0, 100 - (int)(t / 2));   // drains from 100 → 0 over ~200 s
            brain.onPositionReceived(new PositionUpdate(lat, lon, alt, battery));
        }));
        simTimeline.setCycleCount(Timeline.INDEFINITE);
        simTimeline.play();
    }

    private void stopSimulation() {
        if (simTimeline != null) { simTimeline.stop(); simTimeline = null; }
        simRunning = false;
        Platform.runLater(() -> brain.clearDronePreview());
        setStatus("Disconnected", "#8B0000");
        if (droneSimulateButton != null) droneSimulateButton.setText("Simulate");
        if (sendMissionButton   != null) sendMissionButton.setDisable(true);
        resetTelemetryDisplay();
    }

    // ── Mission ───────────────────────────────────────────────────────────────

    @FXML
    public void sendMissionArea() {
        try {
            double lat1 = Double.parseDouble(x1_lat.getText().trim());
            double lon1 = Double.parseDouble(x1_long.getText().trim());
            double lat2 = Double.parseDouble(x2_lat.getText().trim());
            double lon2 = Double.parseDouble(x2_long.getText().trim());
            double lat3 = Double.parseDouble(x3_lat.getText().trim());
            double lon3 = Double.parseDouble(x3_long.getText().trim());
            double lat4 = Double.parseDouble(x4_lat.getText().trim());
            double lon4 = Double.parseDouble(x4_long.getText().trim());

            List<MapPoint> area = List.of(
                new MapPoint(lat1, lon1),
                new MapPoint(lat2, lon2),
                new MapPoint(lat3, lon3),
                new MapPoint(lat4, lon4)
            );

            if (activeConnection != null && activeConnection.isActive()) {
                boolean ok = activeConnection.sendMissionArea(area);
                setMissionStatus(ok ? "SENT ✓" : "SEND FAILED");
            } else {
                // Simulation mode — just acknowledge visually
                setMissionStatus("SET (sim)");
            }
        } catch (NumberFormatException ex) {
            setMissionStatus("INVALID COORDS");
        }
    }

    // ── Path ──────────────────────────────────────────────────────────────────

    @FXML
    public void clearDronePath() {
        Platform.runLater(() -> brain.clearDronePreview());
    }

    // ── Coordinate listen stubs (handled by PolygonPoint internally) ──────────

    @FXML public void setX1ListenMode() {}
    @FXML public void setX2ListenMode() {}
    @FXML public void setX3ListenMode() {}
    @FXML public void setX4ListenMode() {}
    @FXML public void updateCordinates() {}
    @FXML public void clearCordinates()  {}

    // ── Telemetry callback (always on FX thread via Brain) ────────────────────

    private void onTelemetryUpdate(DroneState state) {
        if (state.hasPosition()) {
            setText(latValueLabel, String.format("%.6f", state.getCurrentLat()));
            setText(lonValueLabel, String.format("%.6f", state.getCurrentLon()));
            setText(altValueLabel, String.format("%.1f m", state.getCurrentAlt()));
        } else {
            setText(latValueLabel, "---");
            setText(lonValueLabel, "---");
            setText(altValueLabel, "---");
        }

        int battery = state.getCurrentBattery();
        if (battery >= 0) {
            setText(batteryValueLabel, battery + " %");
            if (batteryBar != null) {
                batteryBar.setProgress(battery / 100.0);
                batteryBar.getStyleClass().removeAll("battery-high", "battery-medium", "battery-low");
                if (battery > 50)      batteryBar.getStyleClass().add("battery-high");
                else if (battery > 20) batteryBar.getStyleClass().add("battery-medium");
                else                   batteryBar.getStyleClass().add("battery-low");
            }
        } else {
            setText(batteryValueLabel, "---");
        }

        int pts = state.getPathPoints().size();
        setText(pathPointsLabel, pts + " pts");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setStatus(String text, String hexColor) {
        if (droneStatusLabel == null) return;
        droneStatusLabel.setText(text);
        droneStatusLabel.setStyle("-fx-background-color: " + hexColor + ";");
    }

    private void setMissionStatus(String text) {
        if (missionStatusLabel != null) missionStatusLabel.setText(text);
    }

    private void resetTelemetryDisplay() {
        setText(latValueLabel,     "---");
        setText(lonValueLabel,     "---");
        setText(altValueLabel,     "---");
        setText(batteryValueLabel, "---");
        setText(pathPointsLabel,   "0 pts");
        if (batteryBar != null) {
            batteryBar.setProgress(0);
            batteryBar.getStyleClass().removeAll("battery-high", "battery-medium", "battery-low");
        }
        if (sendMissionButton != null) sendMissionButton.setDisable(true);
    }

    private static void setText(Label label, String value) {
        if (label != null) label.setText(value);
    }

    private MapPolygon buildPolygon() {
        PolygonPoint p1 = new PolygonPoint(x1_long, x1_lat, x1_listen_button);
        PolygonPoint p2 = new PolygonPoint(x2_long, x2_lat, x2_listen_button);
        PolygonPoint p3 = new PolygonPoint(x3_long, x3_lat, x3_listen_button);
        PolygonPoint p4 = new PolygonPoint(x4_long, x4_lat, x4_listen_button);
        return new MapPolygon(List.of(p1, p2, p3, p4));
    }
}
