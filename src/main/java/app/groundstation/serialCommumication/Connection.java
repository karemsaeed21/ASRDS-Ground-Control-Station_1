package app.groundstation.serialCommumication;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import java.io.Serial;

public class Connection {
//    private Serial serial ;
    private SerialPort serialPort;

    public static void main(String[] args) throws InterruptedException {
      HandShake handShake = new HandShake();
      HandShakeResult result = handShake.makeHandShake("RPI001");
      if (result == null)
        System.out.println("HandShake Faild");
      else {
          if (result.isHandShakeSuccess())
          System.out.println("Port : "+result.getPort().getSystemPortName()+" ,ID: "+result.getSessionID());
            else {
              System.out.println("Hand Shake is False");
          }
      }
    }


}
