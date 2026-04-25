package app.groundstation.map;

import com.gluonhq.maps.MapLayer;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * Map layer that shows the current drone position as a circle (marker).
 */
public class DroneMarkerLayer extends MapLayer {

    private static final double MARKER_RADIUS = 8.0;

    private double lat;
    private double lon;
    private boolean visible;
    private final Circle circle;

    public DroneMarkerLayer() {
        this.circle = new Circle(MARKER_RADIUS);
        this.circle.setFill(Color.rgb(255, 50, 50, 0.9));
        this.circle.setStroke(Color.WHITE);
        this.circle.setStrokeWidth(2.0);
        this.circle.setVisible(false);
        this.getChildren().add(circle);
    }

    /**
     * Set the drone position and show the marker.
     */
    public void setPosition(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
        this.visible = true;
        this.markDirty();
    }

    /**
     * Hide the marker (e.g. when drone disconnects).
     */
    public void hide() {
        this.visible = false;
        this.markDirty();
    }

    @Override
    protected void layoutLayer() {
        if (visible) {
            Point2D screen = this.getMapPoint(lat, lon);
            circle.setCenterX(screen.getX());
            circle.setCenterY(screen.getY());
            circle.setVisible(true);
        } else {
            circle.setVisible(false);
        }
    }
}
