package app.groundstation.serialCommumication;

import app.groundstation.serialCommumication.DataHandlers.PositionParser;
import app.groundstation.serialCommumication.DataHandlers.PositionUpdate;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Continuously reads serial data from the drone port, parses POS,lat,lon,alt lines,
 * and invokes the callback for each position update. Run start() after port is open.
 */
public class DroneTelemetryReader {

    private final SerialPort port;
    private final PositionParser parser = new PositionParser();
    private final java.util.function.Consumer<PositionUpdate> onPosition;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private SerialPortDataListener listener;

    public DroneTelemetryReader(SerialPort port, java.util.function.Consumer<PositionUpdate> onPosition) {
        this.port = port;
        this.onPosition = onPosition;
    }

    /**
     * Start listening for position data. Port must already be open.
     */
    public void start() {
        if (!port.isOpen()) {
            return;
        }
        if (running.getAndSet(true)) {
            return;
        }
        parser.clear();
        listener = new SerialPortDataListener() {
            @Override
            public int getListeningEvents() {
                return SerialPort.LISTENING_EVENT_DATA_RECEIVED;
            }

            @Override
            public void serialEvent(SerialPortEvent event) {
                byte[] data = event.getReceivedData();
                if (data == null || data.length == 0) {
                    return;
                }
                List<PositionUpdate> updates = parser.parse(data);
                for (PositionUpdate pos : updates) {
                    try {
                        onPosition.accept(pos);
                    } catch (Exception e) {
                        System.err.println("DroneTelemetryReader callback error: " + e.getMessage());
                    }
                }
            }
        };
        port.addDataListener(listener);
    }

    /**
     * Stop listening. Does not close the port.
     */
    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        if (listener != null && port.isOpen()) {
            port.removeDataListener();
            listener = null;
        }
    }

    public boolean isRunning() {
        return running.get();
    }
}
