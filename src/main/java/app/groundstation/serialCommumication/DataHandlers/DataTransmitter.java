package app.groundstation.serialCommumication.DataHandlers;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import java.io.Serial;

public class DataTransmitter {



    private SerialPort serialPort;
    private volatile DataContainer container;
    private volatile boolean dataRecived = false;


    public DataTransmitter(SerialPort serialPort) {
        this.serialPort = serialPort;
        this.container = new DataContainer();
    }

    public DataTransmitter() {

        this.container = new DataContainer();
    }

    public DataContainer ReciveData() throws InterruptedException {
        dataRecived = false;
        container.setRowData(null); // clear stale data from previous port
        if (serialPort == null || !serialPort.isOpen()){
            return null;
        }

        this.serialPort.addDataListener(new SerialPortDataListener() {
            @Override
            public int getListeningEvents() {
                return SerialPort.LISTENING_EVENT_DATA_RECEIVED;
            }

            @Override
            public void serialEvent(SerialPortEvent serialPortEvent) {
                if (dataRecived) return; // first chunk wins; ignore subsequent events before listener removal
                container.setRowData(serialPortEvent.getReceivedData());
                dataRecived = true;
            }
        });
        long start = System.currentTimeMillis();
        long timeout = 3000;

        while (!dataRecived && System.currentTimeMillis() - start < timeout) {
            Thread.sleep(10);
        }
        serialPort.removeDataListener();

        return dataRecived? this.container:null;
    }

    public void setSerialPort(SerialPort serialPort) {
        this.serialPort = serialPort;
    }
}
