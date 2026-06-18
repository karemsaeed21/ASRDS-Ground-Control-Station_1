package app.groundstation.map;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

public class PolygonPoint {

    private double latitude ;
    private double longitude;

    private TextField longitudeField;
    private TextField latitudeField;
    private Button listenButton;

    private boolean listen = false;

    public PolygonPoint(TextField longitudeField, TextField latitudeField, Button listenButton) {
        this.longitudeField = longitudeField;
        this.latitudeField = latitudeField;
        this.listenButton = listenButton;
        this.listenButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                ActiveListen();
            }
        });
    }


    private void ActiveListen(){
        this.listen = true;
    }

    private void DeactiveListen(){
        this.listen = false;
    }

    public void CordinatesUpdate(double _long, double _lat){
        if (this.listen){
            setLongitude(_long);
            setLatitude(_lat);
            updateLatitudeFieldValue(_lat);
            updateLongitudeFieldValue(_long);
            DeactiveListen();

        }
    }



    public void updateLongitudeFieldValue(double _long){
        this.longitudeField.setText(_long+"");
    }

    public void updateLatitudeFieldValue(double _lat){
        this.latitudeField.setText(_lat+"");
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    /** Read lat/lon from the text fields into internal state (used before drawing). */
    public void syncFromFields() {
        try {
            String latText = latitudeField.getText().trim();
            String lonText = longitudeField.getText().trim();
            if (!latText.isEmpty()) {
                setLatitude(Double.parseDouble(latText));
            }
            if (!lonText.isEmpty()) {
                setLongitude(Double.parseDouble(lonText));
            }
        } catch (NumberFormatException ignored) {
        }
    }

    public void setFields(double lat, double lon) {
        setLatitude(lat);
        setLongitude(lon);
        updateLatitudeFieldValue(lat);
        updateLongitudeFieldValue(lon);
    }
}
