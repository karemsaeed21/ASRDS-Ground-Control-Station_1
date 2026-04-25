package app.groundstation.map;

import com.gluonhq.maps.MapLayer;
import com.gluonhq.maps.MapPoint;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;

import java.util.ArrayList;
import java.util.List;

public class MapPolygon extends MapLayer {


    private List<PolygonPoint> polygonPoints ;
    private Polygon polygon;

    public MapPolygon (List<PolygonPoint> polygonPoints){
        this.polygonPoints = polygonPoints;
        this.polygon = new Polygon();

        // Set initial styles (Semi-transparent blue)
        polygon.setFill(Color.rgb(0, 150, 255, 0.4));
        polygon.setStroke(Color.BLUE);
        polygon.setStrokeWidth(2.0);

        // The polygon is initially invisible until draw() is called
        polygon.setVisible(false);

        this.getChildren().add(polygon);
    }

    public void listenHandle(double _long, double _lat){
        for (PolygonPoint point : polygonPoints){
            point.CordinatesUpdate(_long,_lat);
        }
    }

    public void draw() {



        this.polygon.setVisible(true); // Make it visible
        this.markDirty(); // Trigger layoutLayer() to redraw
    }

    public void clear() {


        this.polygon.setVisible(false); // Hide it
        this.markDirty(); // Refresh the layer
    }

    @Override
    protected void layoutLayer() {
        // Only calculate screen points if the polygon is visible and has points
        if (polygon.isVisible() ) {
            polygon.getPoints().clear();

            for (PolygonPoint point : polygonPoints) {
                // Using 'this.getMapPoint' to avoid the module access error
                Point2D screenPoint = this.getMapPoint(point.getLatitude(), point.getLongitude());
                polygon.getPoints().addAll(screenPoint.getX(), screenPoint.getY());
            }
        }
    }

}
