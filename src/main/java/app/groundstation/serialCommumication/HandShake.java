package app.groundstation.serialCommumication;

import app.groundstation.serialCommumication.DataHandlers.DataContainer;
import app.groundstation.serialCommumication.DataHandlers.DataTransmitter;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

public class HandShake {


    private volatile HandShakeResult handShakeResult;
    private SerialPort serialPort;
    private DataTransmitter transmitter;
    private volatile boolean sessionRecived;
    private static String handShakeTemp = "HSH,";

    public HandShakeResult makeHandShake(String ID) {
        transmitter = new DataTransmitter();
        SerialPort[] ports = SerialPort.getCommPorts();
        System.err.println("[Drone] HandShake: found " + ports.length + " port(s)");
        for (SerialPort p : ports) {
            System.err.println("[Drone]   - " + p.getSystemPortName());
        }
        if (ports.length == 0) {
            System.err.println("[Drone] No serial ports found. Trying macOS pty fallback...");
        }
        for (SerialPort port : ports) {

            sessionRecived = false;
            handShakeResult = null;

            try {
                if (!port.openPort())
                    continue;

                System.err.println("[Drone] Port " + port.getSystemPortName() + " opened");

                port.setBaudRate(115200);

                // Drain any stale bytes buffered while the port was closed (e.g. on reconnect)
                drainBuffer(port);

                String msg = handShakeTemp + ID;
                port.writeBytes(msg.getBytes(), msg.length());
                transmitter.setSerialPort(port);
                DataContainer container = transmitter.ReciveData();
                if (container == null)
                    continue;

                byte[] newData = container.getRowData();
                String message = new String(newData).trim();
                System.err.println("[Drone] Message from " + port.getSystemPortName() + " => " + message);
                if (message.contains("HSHAC")) {
                    HandShakeResult result = extractHandShake(port, message);
                    if (result != null) return result;
                }

                port.closePort();

            } catch (Exception e) {
                port.closePort();
            }
        }

        // Fallback: on macOS, getCommPorts() may not list socat ptys; try common pty paths
        String os = System.getProperty("os.name", "").toLowerCase();
        if (handShakeResult == null && (os.contains("mac") || os.contains("darwin"))) {
            // Try middle range first (010..025) where socat ptys often are, then rest.
            // This way we hit ttys013 quickly before simulator gets handshake from wrong port.
            System.err.println("[Drone] Trying macOS pty fallback (/dev/ttys010..025 first, then 001..050)...");
            System.err.flush();
            int[] order = new int[50];
            int idx = 0;
            for (int i = 10; i <= 25; i++) order[idx++] = i;
            for (int i = 1; i <= 9; i++) order[idx++] = i;
            for (int i = 26; i <= 50; i++) order[idx++] = i;
            for (int i : order) {
                String path = "/dev/ttys" + String.format("%03d", i);
                try {
                    SerialPort port = SerialPort.getCommPort(path);
                    if (!port.openPort()) continue;
                    port.setBaudRate(115200);
                    String msg = handShakeTemp + ID;
                    port.writeBytes(msg.getBytes(), msg.length());
                    transmitter.setSerialPort(port);
                    DataContainer container = transmitter.ReciveData();
                    if (container == null) { port.closePort(); continue; }
                    byte[] newData = container.getRowData();
                    if (newData == null) { port.closePort(); continue; }
                    String message = new String(newData).trim();
                    if (!message.startsWith("HSHAC")) {
                        System.err.println("[Drone] pty " + path + " replied with: '" + message + "' (expected HSHAC...)");
                    }
                    if (message.contains("HSHAC")) {
                        HandShakeResult result = extractHandShake(port, message);
                        if (result != null) {
                            System.err.println("[Drone] HandShake OK on fallback port " + path);
                            return result;
                        }
                    }
                    port.closePort();
                } catch (Exception ignored) {
                    // try next
                }
            }
            System.err.println("[Drone] No pty responded to handshake.");
        }

        return null;
    }

    /**
     * Read and discard any bytes already in the OS receive buffer.
     * Runs for ~200 ms so that buffered data from a previous session is gone
     * before the handshake message is sent.
     */
    private static void drainBuffer(SerialPort port) {
        byte[] discard = new byte[256];
        long end = System.currentTimeMillis() + 200;
        do {
            int n = port.bytesAvailable();
            if (n > 0) port.readBytes(discard, Math.min(n, discard.length));
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        } while (System.currentTimeMillis() < end);
    }

    /**
     * Find "HSHAC" anywhere in the received bytes and extract the session ID.
     * Robust against stale garbage data prepended before the HSHAC token.
     */
    private static HandShakeResult extractHandShake(SerialPort port, String message) {
        int idx = message.indexOf("HSHAC");
        if (idx < 0) return null;
        String[] parts = message.substring(idx).trim().split("\\s+");
        if (parts.length >= 2) {
            try {
                int sessionID = Integer.parseInt(parts[1]);
                return new HandShakeResult(port, true, sessionID);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

}
