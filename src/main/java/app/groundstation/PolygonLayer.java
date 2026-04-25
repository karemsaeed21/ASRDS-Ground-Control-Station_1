package app.groundstation;

import com.gluonhq.maps.MapLayer;
import com.gluonhq.maps.MapPoint;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;

import java.util.ArrayList;
import java.util.List;

public class PolygonLayer extends MapLayer {

    private final List<MapPoint> mapPoints;
    private final Polygon polygon;

    // 1. Constructor takes no arguments
    public PolygonLayer() {
        this.mapPoints = new ArrayList<>();
        this.polygon = new Polygon();

        // Set initial styles (Semi-transparent blue)
        polygon.setFill(Color.rgb(0, 150, 255, 0.4));
        polygon.setStroke(Color.BLUE);
        polygon.setStrokeWidth(2.0);

        // The polygon is initially invisible until draw() is called
        polygon.setVisible(false);

        this.getChildren().add(polygon);
    }

    // 2. Draw function: Takes a list of points and draws the polygon
    public void draw(List<MapPoint> newPoints) {
        this.mapPoints.clear();
        this.mapPoints.addAll(newPoints);

        this.polygon.setVisible(true); // Make it visible
        this.markDirty(); // Trigger layoutLayer() to redraw
    }

    // 3. Clear function: Undraws the polygon
    public void clear() {
        this.mapPoints.clear();
        this.polygon.getPoints().clear();

        this.polygon.setVisible(false); // Hide it
        this.markDirty(); // Refresh the layer
    }

    @Override
    protected void layoutLayer() {

        if (polygon.isVisible() && !mapPoints.isEmpty()) {
            polygon.getPoints().clear();

            for (MapPoint mapPoint : mapPoints) {

                Point2D screenPoint = this.getMapPoint(mapPoint.getLatitude(), mapPoint.getLongitude());
                polygon.getPoints().addAll(screenPoint.getX(), screenPoint.getY());
            }
        }
    }
}