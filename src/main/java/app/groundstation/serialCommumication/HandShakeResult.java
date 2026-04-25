package app.groundstation.serialCommumication;

import com.fazecast.jSerialComm.SerialPort;

public class HandShakeResult {


    private SerialPort port;
    private boolean HandShakeSuccess;
    private int SessionID;

    public HandShakeResult(SerialPort port, boolean handShakeState, int sessionID) {
        this.port = port;
        HandShakeSuccess = handShakeState;
        SessionID = sessionID;
    }

    public SerialPort getPort() {
        return port;
    }



    public int getSessionID() {
        return SessionID;
    }

    public boolean isHandShakeSuccess() {
        return HandShakeSuccess;
    }
}
