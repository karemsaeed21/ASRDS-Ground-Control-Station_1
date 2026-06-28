package app.groundstation.network;

import org.json.JSONObject;
import app.groundstation.map.WorldPoint;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

public class TcpDroneServer {
    private final int port;
    private final ServerCallback callback;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread serverThread;
    private volatile boolean running = false;

    public TcpDroneServer(int port, ServerCallback callback) {
        this.port = port;
        this.callback = callback;
    }

    public void start() {
        running = true;
        serverThread = new Thread(this::runServer);
        serverThread.start();
    }

    public void stop() {
        running = false;
        try {
            if (clientSocket != null) clientSocket.close();
            if (serverSocket != null) serverSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void runServer() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("[TCP] Server listening on port " + port);

            // Accept single client connection
            clientSocket = serverSocket.accept();
            System.out.println("[TCP] Client connected: " + clientSocket.getRemoteSocketAddress());
            
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            String line;
            while (running && (line = in.readLine()) != null) {
                try {
                    handleMessage(new JSONObject(line));
                } catch (Exception e) {
                    System.err.println("[TCP] Parse error: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            if (running) {
                System.err.println("[TCP] Server error: " + e.getMessage());
            }
        } finally {
            if (running) {
                callback.onDroneDisconnected();
            }
            stop();
        }
    }

    private void handleMessage(JSONObject msg) {
        if (!msg.has("type")) return;
        String type = msg.getString("type");
        String id = msg.optString("id");
        JSONObject payload = msg.optJSONObject("payload");
        if (payload == null) payload = new JSONObject();

        switch (type) {
            case "HELLO":
                callback.onDroneConnected();
                break;
            case "SEARCH_AREA_REQUEST":
                callback.onSearchAreaRequested();
                break;
            case "STATUS":
                JSONObject pos = payload.optJSONObject("position");
                if (pos != null) {
                    callback.onStatusReceived(
                        pos.optDouble("x"),
                        pos.optDouble("y"),
                        pos.optDouble("z"),
                        payload.optDouble("heading_rad"),
                        payload.optString("state")
                    );
                }
                break;
            case "HUMAN_DETECTED":
                JSONObject hPos = payload.optJSONObject("estimated_human_position");
                if (hPos != null) {
                    callback.onHumanDetected(id, hPos.optDouble("x"), hPos.optDouble("y"), payload.optDouble("confidence"));
                }
                break;
            case "SEARCH_COMPLETE":
                callback.onSearchComplete();
                break;
            default:
                // Ignore unknown types
                break;
        }
    }

    public void sendSearchArea(List<WorldPoint> area) {
        if (out == null || area.size() < 4) return;
        
        double xMin = Double.MAX_VALUE;
        double yMin = Double.MAX_VALUE;
        double xMax = Double.MIN_VALUE;
        double yMax = Double.MIN_VALUE;

        for (WorldPoint p : area) {
            if (p.getX() < xMin) xMin = p.getX();
            if (p.getY() < yMin) yMin = p.getY();
            if (p.getX() > xMax) xMax = p.getX();
            if (p.getY() > yMax) yMax = p.getY();
        }

        JSONObject payload = new JSONObject();
        payload.put("x_min", xMin);
        payload.put("y_min", yMin);
        payload.put("x_max", xMax);
        payload.put("y_max", yMax);
        // altitude_m is optional, leaving it out so drone uses default

        JSONObject msg = ProtocolMessage.createEnvelope("SEARCH_AREA", payload);
        out.println(msg.toString());
    }

    public void sendDetectionResponse(String replyToId, boolean continueSearch) {
        if (out == null) return;

        JSONObject payload = new JSONObject();
        payload.put("decision", continueSearch ? "CONTINUE_SEARCH" : "RETURN_HOME");

        JSONObject msg = ProtocolMessage.createReplyEnvelope("DETECTION_RESPONSE", replyToId, payload);
        out.println(msg.toString());
    }

    public void sendStartMission() {
        if (out == null) return;
        JSONObject msg = ProtocolMessage.createEnvelope("START_MISSION", new JSONObject());
        out.println(msg.toString());
    }

    public void sendAbort() {
        if (out == null) return;
        JSONObject msg = ProtocolMessage.createEnvelope("ABORT", new JSONObject());
        out.println(msg.toString());
    }
}
