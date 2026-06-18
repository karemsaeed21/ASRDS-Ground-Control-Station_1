package app.groundstation.serialCommumication;

import com.fazecast.jSerialComm.SerialPort;

/**
 * Manages the serial link to the transmission device (separate from the drone).
 * Uses the same handshake protocol with device ID TX001.
 */
public class TransmitterConnection {

    public static final String TRANSMITTER_ID = "TX001";

    private SerialPort port;
    private int sessionId = -1;
    private volatile boolean connected;

    public boolean connect() {
        HandShakeResult result = new HandShake().makeHandShake(TRANSMITTER_ID);
        if (result != null && result.isHandShakeSuccess()) {
            port = result.getPort();
            sessionId = result.getSessionID();
            connected = true;
            return true;
        }
        connected = false;
        port = null;
        sessionId = -1;
        return false;
    }

    public void disconnect() {
        connected = false;
        if (port != null && port.isOpen()) {
            port.closePort();
        }
        port = null;
        sessionId = -1;
    }

    public boolean isConnected() {
        return connected && port != null && port.isOpen();
    }

    public int getSessionId() {
        return sessionId;
    }

    public SerialPort getPort() {
        return port;
    }
}
