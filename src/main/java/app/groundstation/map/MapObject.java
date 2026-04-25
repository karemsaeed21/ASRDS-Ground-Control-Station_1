package app.groundstation.map;

import com.gluonhq.maps.MapPoint;
import com.gluonhq.maps.MapView;
import javafx.scene.control.Button;

import java.util.List;

public class MapObject {

    private final MapView mapView;
    private final MapPolygon mapPolygon;
    private final DroneMarkerLayer droneMarkerLayer;
    private final PathLayer pathLayer;
    private final Button drowButton;
    private final Button clearButton;

    public MapObject(MapView mapView, MapPolygon mapPolygon, Button drowButton, Button clearButton) {
        this.mapView = mapView;
        this.mapPolygon = mapPolygon;
        this.droneMarkerLayer = new DroneMarkerLayer();
        this.pathLayer = new PathLayer();
        this.drowButton = drowButton;
        this.clearButton = clearButton;

        if (this.clearButton != null) {
            this.clearButton.setOnMouseClicked(event -> this.mapPolygon.clear());
        }
        if (this.drowButton != null) {
            this.drowButton.setOnMouseClicked(event -> this.mapPolygon.draw());
        }
        this.mapView.setOnMouseClicked(event -> {
            double x = event.getX();
            double y = event.getY();
            MapPoint clickedPoint = this.mapView.getMapPosition(x, y);
            mapPolygon.listenHandle(clickedPoint.getLongitude(), clickedPoint.getLatitude());
        });

        mapView.addLayer(this.mapPolygon);
        mapView.addLayer(this.pathLayer);
        mapView.addLayer(this.droneMarkerLayer);
        mapView.setCenter(29.333938744108426, 31.02326206313191);
        mapView.onZoomProperty().addListener((obs, oldZoom, newZoom) ->
            System.out.println("Zoom changed from " + oldZoom + " to " + newZoom));
        mapView.setZoom(6);
        mapView.setCenter(31.19342, 29.90032);
    }

    /**
     * Update drone position marker on the map. Call from JavaFX thread.
     */
    public void updateDronePosition(double lat, double lon) {
        droneMarkerLayer.setPosition(lat, lon);
    }

    /**
     * Update flight path polyline. Call from JavaFX thread.
     */
    public void updateDronePath(List<MapPoint> pathPoints) {
        pathLayer.setPath(pathPoints);
    }

    /**
     * Hide drone marker and path (e.g. when drone disconnects).
     */
    public void clearDronePreview() {
        droneMarkerLayer.hide();
        pathLayer.clear();
    }
}
