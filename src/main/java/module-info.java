module app.groundstation {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.fazecast.jSerialComm;
    requires org.json;


    opens app.groundstation to javafx.fxml;
    exports app.groundstation;
}
