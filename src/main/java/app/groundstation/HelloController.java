package app.groundstation;

import app.groundstation.mainBrain.Brain;
import app.groundstation.mainBrain.DroneState;
import app.groundstation.mainBrain.FlightReport;
import app.groundstation.map.MapObject;
import app.groundstation.map.MapPolygon;
import app.groundstation.map.PolygonPoint;
import app.groundstation.serialCommumication.ActiveConnection;
import app.groundstation.serialCommumication.DataHandlers.PositionUpdate;
import app.groundstation.serialCommumication.DroneTelemetryReader;
import app.groundstation.serialCommumication.HandShake;
import app.groundstation.serialCommumication.HandShakeResult;
import app.groundstation.serialCommumication.TransmitterConnection;
import app.groundstation.network.TcpDroneServer;
import app.groundstation.network.ServerCallback;
import app.groundstation.map.WorldPoint;
import java.util.Optional;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.net.URL;
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
    @FXML private Button defineSquareButton;

    // ── Connection controls ────────────────────────────────────────────────────
    @FXML private Label  transmitterStatusLabel;
    @FXML private Button transmitterConnectButton;
    @FXML private Label  droneStatusLabel;
    @FXML private Button droneConnectButton;
    @FXML private Button droneSimulateButton;
    @FXML private Button checkConnectivityButton;
    @FXML private Label  connectivityDetailLabel;

    // ── Map ───────────────────────────────────────────────────────────────────
    @FXML private StackPane MapContainer;

    // ── Telemetry readouts (right panel) ──────────────────────────────────────
    @FXML private Label latValueLabel;
    @FXML private Label lonValueLabel;
    @FXML private Label altValueLabel;
    @FXML private Label batteryValueLabel;
    @FXML private ProgressBar batteryBar;
    @FXML private Label pathPointsLabel;
    @FXML private CheckBox followDroneCheckBox;

    // ── Mission controls (right panel) ────────────────────────────────────────
    @FXML private Label  missionStatusLabel;
    @FXML private Button sendMissionButton;
    @FXML private Button clearPathButton;

    // ── Flight controls ───────────────────────────────────────────────────────
    @FXML private Label  flightStatusLabel;
    @FXML private Button startNewFlightButton;
    @FXML private Button startMissionButton;
    @FXML private Button stopFlightButton;

    // ── Decision controls ─────────────────────────────────────────────────────
    @FXML private Button rtlButton;
    @FXML private Button holdButton;
    @FXML private Button resumeButton;
    @FXML private Button landButton;

    // ── Reports ───────────────────────────────────────────────────────────────
    @FXML private Label reportFlightIdLabel;
    @FXML private Label reportDurationLabel;
    @FXML private Label reportMaxAltLabel;
    @FXML private Label reportEventsLabel;
    @FXML private Button exportReportButton;

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final String DRONE_ID       = "RPI001";
    private static final double SIM_LAT        = 500.0;
    private static final double SIM_LON        = 500.0;
    private static final double SIM_ALT        = 100.0;
    private static final double SIM_RADIUS_DEG = 150.0;

    // ── State ─────────────────────────────────────────────────────────────────
    private MapObject              mapObject;
    private Brain                  brain;
    private ActiveConnection       activeConnection;
    private DroneTelemetryReader   telemetryReader;
    private TransmitterConnection  transmitterConnection;
    private HandShakeResult        handshakeResult;
    private TcpDroneServer         tcpDroneServer;
    private FlightReport           flightReport;
    private volatile boolean       droneConnected;
    private volatile boolean       transmitterConnected;
    private volatile boolean       flightActive;
    private volatile boolean       missionSent;
    private Timeline               simTimeline;
    private volatile boolean       simRunning;
    private Timeline               reportRefreshTimeline;

    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        MapPolygon polygon = buildPolygon();
        mapObject = new MapObject(MapContainer, polygon, DrowButton, ClearButton);

        brain = new Brain(mapObject);
        brain.setTelemetryListener(this::onTelemetryUpdate);
        flightReport = new FlightReport();
        transmitterConnection = new TransmitterConnection();

        resetTelemetryDisplay();
        updateFlightControls();
        updateReportDisplay();

        reportRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateReportDisplay()));
        reportRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        reportRefreshTimeline.play();
    }

    // ── Transmitter connection ────────────────────────────────────────────────

    @FXML
    public void toggleTransmitterConnection() {
        if (transmitterConnected) disconnectTransmitter();
        else                      connectTransmitter();
    }

    private void connectTransmitter() {
        setTransmitterStatus("Connecting...", "#555555");
        if (transmitterConnectButton != null) transmitterConnectButton.setDisable(true);

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                return transmitterConnection.connect();
            }
        };

        task.setOnSucceeded(e -> {
            boolean ok = Boolean.TRUE.equals(task.getValue());
            transmitterConnected = ok;
            if (ok) {
                setTransmitterStatus("Connected", "#2e7d32");
                if (transmitterConnectButton != null) transmitterConnectButton.setText("Disconnect");
                flightReport.recordEvent("Transmitter connected (serial)");
            } else {
                setTransmitterStatus("Disconnected", "#8B0000");
            }
            if (transmitterConnectButton != null) transmitterConnectButton.setDisable(false);
            updateFlightControls();
        });

        task.setOnFailed(e -> {
            setTransmitterStatus("Disconnected", "#8B0000");
            if (transmitterConnectButton != null) transmitterConnectButton.setDisable(false);
        });

        new Thread(task).start();
    }

    private void disconnectTransmitter() {
        transmitterConnection.disconnect();
        transmitterConnected = false;
        setTransmitterStatus("Disconnected", "#8B0000");
        if (transmitterConnectButton != null) transmitterConnectButton.setText("Connect");
        flightReport.recordEvent("Transmitter disconnected");
        updateFlightControls();
    }

    // ── Drone connection ──────────────────────────────────────────────────────

    @FXML
    public void toggleDroneConnection() {
        if (droneConnected) disconnectDrone();
        else                connectDrone();
    }

    private void connectDrone() {
        setDroneStatus("Listening on 5050...", "#555555");
        if (droneConnectButton != null) droneConnectButton.setDisable(true);
        if (tcpDroneServer != null) tcpDroneServer.stop();

        tcpDroneServer = new TcpDroneServer(5050, new ServerCallback() {
            @Override
            public void onDroneConnected() {
                Platform.runLater(() -> {
                    droneConnected = true;
                    setDroneStatus("Connected", "#2e7d32");
                    if (droneConnectButton != null) {
                        droneConnectButton.setText("Disconnect");
                        droneConnectButton.setDisable(false);
                    }
                    flightReport.recordEvent("Drone connected (TCP simulation)");
                });
            }

            @Override
            public void onDroneDisconnected() {
                Platform.runLater(() -> {
                    disconnectDrone();
                });
            }

            @Override
            public void onSearchAreaRequested() {
                Platform.runLater(() -> {
                    setMissionStatus("AWAITING AREA");
                });
            }

            @Override
            public void onStatusReceived(double x, double y, double z, double headingRad, String state) {
                Platform.runLater(() -> {
                    brain.onPositionReceived(new PositionUpdate(y, x, z, 0)); // Note: existing uses Y for lat, X for lon
                    if (altValueLabel != null) altValueLabel.setText(String.format("%.1f m", z));
                    if (state != null && !state.isEmpty() && flightStatusLabel != null) setFlightStatus(state);
                });
            }

            @Override
            public void onHumanDetected(String messageId, double hX, double hY, double confidence) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("HUMAN DETECTED");
                    alert.setHeaderText(String.format("Human spotted at X:%.1f Y:%.1f", hX, hY));
                    alert.setContentText(String.format("Confidence: %.0f%%. What should the drone do?", confidence * 100));
                    
                    ButtonType btnContinue = new ButtonType("Continue Search");
                    ButtonType btnReturn = new ButtonType("Return Home");
                    alert.getButtonTypes().setAll(btnContinue, btnReturn);
                    
                    alert.showAndWait().ifPresent(type -> {
                        boolean cont = (type == btnContinue);
                        tcpDroneServer.sendDetectionResponse(messageId, cont);
                        flightReport.recordEvent("Human detected. Decision: " + (cont ? "Continue" : "RTL"));
                    });
                });
            }

            @Override
            public void onSearchComplete() {
                Platform.runLater(() -> {
                    setFlightStatus("SEARCH COMPLETE");
                    flightReport.recordEvent("Search complete");
                });
            }
        });
        
        tcpDroneServer.start();
    }

    private void disconnectDrone() {
        if (tcpDroneServer != null) { tcpDroneServer.stop(); tcpDroneServer = null; }
        if (telemetryReader != null) { telemetryReader.stop(); telemetryReader = null; }
        if (activeConnection != null) { activeConnection.close(); activeConnection = null; }
        handshakeResult = null;
        droneConnected  = false;
        if (flightActive) endFlight("DISCONNECTED");
        Platform.runLater(() -> brain.clearDronePreview());
        setDroneStatus("Disconnected", "#8B0000");
        if (droneConnectButton != null) {
            droneConnectButton.setText("Connect");
            droneConnectButton.setDisable(false);
        }
        if (missionStatusLabel != null) missionStatusLabel.setText("NOT SENT");
        missionSent = false;
        resetTelemetryDisplay();
        updateFlightControls();
    }

    // ── Connectivity check ────────────────────────────────────────────────────

    @FXML
    public void checkConnectivity() {
        StringBuilder detail = new StringBuilder();
        detail.append("TX: ").append(transmitterConnected ? "OK" : "OFF");
        detail.append("  |  Drone: ").append(droneConnected || simRunning ? "OK" : "OFF");

        if (tcpDroneServer != null) {
            detail.append("  |  TCP Server active");
        } else if (activeConnection != null && activeConnection.isActive()) {
            activeConnection.requestStatus();
            detail.append("  |  Status ping sent");
            flightReport.recordEvent("Connectivity check — status ping sent");
        } else if (simRunning) {
            detail.append("  |  Sim active");
        }

        if (connectivityDetailLabel != null) {
            connectivityDetailLabel.setText(detail.toString());
        }
    }

    private void onSerialResponse(String line) {
        Platform.runLater(() -> {
            if (line.startsWith("STATUS")) {
                if (connectivityDetailLabel != null) connectivityDetailLabel.setText("Drone: " + line);
                flightReport.recordEvent("Status response: " + line);
            } else if (line.startsWith("MISSION_ACK")) {
                setMissionStatus("ACK ✓");
                missionSent = true;
                flightReport.setMissionStatus("ACKNOWLEDGED");
            } else if (line.startsWith("MISSION_STARTED")) {
                setFlightStatus("IN FLIGHT");
                flightActive = true;
                flightReport.setFlightStatus("IN FLIGHT");
                flightReport.recordEvent("Mission started by drone");
                updateFlightControls();
            } else if (line.startsWith("DECISION_ACK")) {
                flightReport.recordEvent("Decision acknowledged: " + line);
            }
        });
    }

    // ── Simulation ────────────────────────────────────────────────────────────

    @FXML
    public void toggleDroneSimulate() {
        if (simRunning) stopSimulation();
        else            startSimulation();
    }

    private void startSimulation() {
        simRunning = true;
        setDroneStatus("Simulating", "#e65100");
        if (droneSimulateButton != null) droneSimulateButton.setText("Stop Simulate");
        flightReport.recordEvent("Simulation mode started");

        final long startNanos = System.nanoTime();
        simTimeline = new Timeline(new KeyFrame(Duration.millis(200), e -> {
            double t       = (System.nanoTime() - startNanos) / 1e9;
            double lat     = SIM_LAT + SIM_RADIUS_DEG * Math.sin(t * 0.5);
            double lon     = SIM_LON + SIM_RADIUS_DEG * Math.cos(t * 0.5);
            double alt     = SIM_ALT + 5.0 * Math.sin(t * 0.3);
            int    battery = Math.max(0, 100 - (int)(t / 2));
            brain.onPositionReceived(new PositionUpdate(lat, lon, alt, battery));
        }));
        simTimeline.setCycleCount(Timeline.INDEFINITE);
        simTimeline.play();
        updateFlightControls();
    }

    private void stopSimulation() {
        if (simTimeline != null) { simTimeline.stop(); simTimeline = null; }
        simRunning = false;
        if (flightActive) endFlight("SIM STOPPED");
        Platform.runLater(() -> brain.clearDronePreview());
        setDroneStatus("Disconnected", "#8B0000");
        if (droneSimulateButton != null) droneSimulateButton.setText("Simulate");
        resetTelemetryDisplay();
        updateFlightControls();
    }

    // ── Map / search area ─────────────────────────────────────────────────────

    @FXML
    public void updateCordinates() {
        mapObject.drawSearchArea();
    }

    @FXML
    public void clearCordinates() {
        mapObject.clearSearchArea();
        clearCoordinateFields();
    }

    @FXML
    public void defineSquare() {
        try {
            double lat1 = Double.parseDouble(x1_lat.getText().trim());
            double lon1 = Double.parseDouble(x1_long.getText().trim());
            double lat2 = Double.parseDouble(x2_lat.getText().trim());
            double lon2 = Double.parseDouble(x2_long.getText().trim());

            double minLat = Math.min(lat1, lat2);
            double maxLat = Math.max(lat1, lat2);
            double minLon = Math.min(lon1, lon2);
            double maxLon = Math.max(lon1, lon2);

            x1_lat.setText(String.valueOf(minLat));
            x1_long.setText(String.valueOf(minLon));
            x2_lat.setText(String.valueOf(maxLat));
            x2_long.setText(String.valueOf(minLon));
            x3_lat.setText(String.valueOf(maxLat));
            x3_long.setText(String.valueOf(maxLon));
            x4_lat.setText(String.valueOf(minLat));
            x4_long.setText(String.valueOf(maxLon));

            MapPolygon polygon = mapObject.getMapPolygon();
            List<PolygonPoint> pts = polygon.getPolygonPoints();
            pts.get(0).setFields(minLat, minLon);
            pts.get(1).setFields(maxLat, minLon);
            pts.get(2).setFields(maxLat, maxLon);
            pts.get(3).setFields(minLat, maxLon);
            mapObject.drawSearchArea();
            flightReport.recordEvent("Search square defined");
        } catch (NumberFormatException ex) {
            setMissionStatus("INVALID COORDS");
        }
    }

    @FXML
    public void toggleFollowDrone() {
        boolean follow = followDroneCheckBox != null && followDroneCheckBox.isSelected();
        brain.setFollowDrone(follow);
    }

    @FXML public void setX1ListenMode() {}
    @FXML public void setX2ListenMode() {}
    @FXML public void setX3ListenMode() {}
    @FXML public void setX4ListenMode() {}

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

            List<WorldPoint> area = List.of(
                new WorldPoint(lon1, lat1),
                new WorldPoint(lon2, lat2),
                new WorldPoint(lon3, lat3),
                new WorldPoint(lon4, lat4)
            );

            mapObject.drawSearchArea();

            if (tcpDroneServer != null) {
                tcpDroneServer.sendSearchArea(area);
                setMissionStatus("SENT ✓");
                missionSent = true;
                flightReport.setMissionStatus("SENT");
            } else if (activeConnection != null && activeConnection.isActive()) {
                boolean ok = activeConnection.sendMissionArea(area);
                setMissionStatus(ok ? "SENT ✓" : "SEND FAILED");
                missionSent = ok;
                if (ok) flightReport.setMissionStatus("SENT");
            } else {
                setMissionStatus("SET (sim)");
                missionSent = true;
                flightReport.setMissionStatus("SET (simulation)");
            }
            updateFlightControls();
        } catch (NumberFormatException ex) {
            setMissionStatus("INVALID COORDS");
        }
    }

    // ── Flight lifecycle ──────────────────────────────────────────────────────

    @FXML
    public void startNewFlight() {
        if (flightActive) endFlight("NEW FLIGHT");
        flightReport = new FlightReport();
        flightReport.startNewFlight();
        missionSent = false;
        setMissionStatus("NOT SENT");
        setFlightStatus("PREPARING");
        Platform.runLater(() -> brain.clearDronePreview());
        updateFlightControls();
        updateReportDisplay();
    }

    @FXML
    public void startFlight() {
        if (!isLinkReady()) {
            setFlightStatus("NO LINK");
            return;
        }
        if (!missionSent) {
            setFlightStatus("NO MISSION");
            return;
        }

        flightActive = true;
        setFlightStatus("IN FLIGHT");
        flightReport.setFlightStatus("IN FLIGHT");
        flightReport.recordEvent("Flight started");
        brain.setFollowDrone(followDroneCheckBox != null && followDroneCheckBox.isSelected());

        if (activeConnection != null && activeConnection.isActive()) {
            activeConnection.startMission();
        }
        // For TCP simulation, the drone takes off automatically after receiving SEARCH_AREA.
        
        updateFlightControls();
    }

    @FXML
    public void stopFlight() {
        if (tcpDroneServer != null) {
            tcpDroneServer.sendAbort();
        } else if (activeConnection != null && activeConnection.isActive()) {
            activeConnection.abort();
        }
        endFlight("ABORTED");
    }

    private void endFlight(String reason) {
        flightActive = false;
        setFlightStatus(reason);
        flightReport.endFlight(reason);
        brain.setFollowDrone(false);
        updateFlightControls();
        updateReportDisplay();
    }

    // ── Decisions ─────────────────────────────────────────────────────────────

    @FXML public void sendRtl()    { sendDecision("RTL"); }
    @FXML public void sendHold()   { sendDecision("HOLD"); }
    @FXML public void sendResume() { sendDecision("RESUME"); }
    @FXML public void sendLand()  { sendDecision("LAND"); }

    private void sendDecision(String command) {
        if (!flightActive) return;
        
        if (tcpDroneServer != null && "RTL".equals(command)) {
            tcpDroneServer.sendAbort();
        } else if (activeConnection != null && activeConnection.isActive()) {
            activeConnection.sendDecision(command);
        }
        
        flightReport.recordEvent("Decision sent: " + command);
        if ("LAND".equals(command) || "RTL".equals(command)) {
            endFlight(command);
        }
    }

    // ── Reports ───────────────────────────────────────────────────────────────

    @FXML
    public void exportFlightReport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Flight Report");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        chooser.setInitialFileName("flight_" + flightReport.getFlightId() + ".csv");
        File file = chooser.showSaveDialog(MapContainer.getScene().getWindow());
        if (file == null) return;
        try {
            flightReport.exportCsv(file.toPath(), brain.getDroneState());
            flightReport.recordEvent("Report exported to " + file.getName());
            updateReportDisplay();
        } catch (Exception ex) {
            if (reportEventsLabel != null) reportEventsLabel.setText("Export failed");
        }
    }

    // ── Path ──────────────────────────────────────────────────────────────────

    @FXML
    public void clearDronePath() {
        Platform.runLater(() -> brain.clearDronePreview());
        flightReport.recordEvent("Flight path cleared");
    }

    // ── Telemetry callback ──────────────────────────────────────────────────────

    private void onTelemetryUpdate(DroneState state) {
        if (state.hasPosition()) {
            setText(latValueLabel, String.format("%.2f", state.getCurrentY()));
            setText(lonValueLabel, String.format("%.2f", state.getCurrentX()));
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
        flightReport.updateFromState(state);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isLinkReady() {
        return droneConnected || simRunning;
    }

    private void updateFlightControls() {
        boolean linkReady = isLinkReady();
        boolean canStart  = linkReady && missionSent && !flightActive;

        if (sendMissionButton  != null) sendMissionButton.setDisable(!linkReady);
        if (startMissionButton != null) startMissionButton.setDisable(!canStart);
        if (stopFlightButton   != null) stopFlightButton.setDisable(!flightActive);

        boolean decisionsEnabled = flightActive && linkReady;
        if (rtlButton    != null) rtlButton.setDisable(!decisionsEnabled);
        if (holdButton   != null) holdButton.setDisable(!decisionsEnabled);
        if (resumeButton != null) resumeButton.setDisable(!decisionsEnabled);
        if (landButton   != null) landButton.setDisable(!decisionsEnabled);
    }

    private void updateReportDisplay() {
        if (reportFlightIdLabel != null) reportFlightIdLabel.setText(flightReport.getFlightId());
        if (reportDurationLabel != null) reportDurationLabel.setText(flightReport.getDurationDisplay());
        if (reportMaxAltLabel   != null) {
            double alt = flightReport.getMaxAltitude();
            reportMaxAltLabel.setText(alt > 0 ? String.format("%.1f m", alt) : "---");
        }
        if (reportEventsLabel != null) reportEventsLabel.setText(String.valueOf(flightReport.getEvents().size()));
    }

    private void setDroneStatus(String text, String hexColor) {
        if (droneStatusLabel == null) return;
        droneStatusLabel.setText(text);
        droneStatusLabel.setStyle("-fx-background-color: " + hexColor + "; -fx-text-fill: white; -fx-padding: 3 6;");
    }

    private void setTransmitterStatus(String text, String hexColor) {
        if (transmitterStatusLabel == null) return;
        transmitterStatusLabel.setText(text);
        transmitterStatusLabel.setStyle("-fx-background-color: " + hexColor + "; -fx-text-fill: white; -fx-padding: 3 6;");
    }

    private void setMissionStatus(String text) {
        if (missionStatusLabel != null) missionStatusLabel.setText(text);
    }

    private void setFlightStatus(String text) {
        if (flightStatusLabel != null) flightStatusLabel.setText(text);
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
    }

    private void clearCoordinateFields() {
        for (TextField f : new TextField[]{x1_lat, x1_long, x2_lat, x2_long, x3_lat, x3_long, x4_lat, x4_long}) {
            if (f != null) f.clear();
        }
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
