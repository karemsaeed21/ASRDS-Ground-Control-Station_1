package app.groundstation.map;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import java.util.List;

public class MapObject {

    private final StackPane stackPane;
    private final Canvas canvas;
    private final MapPolygon mapPolygon;
    private final DroneMarkerLayer droneMarkerLayer;
    private final PathLayer pathLayer;
    private final Button drowButton;
    private final Button clearButton;

    // Constants for coordinate mapping
    private static final double IMAGE_SIZE = 1000.0;
    private static final double WORLD_SIZE = 1000.0;
    private static final double SCALE = IMAGE_SIZE / WORLD_SIZE;

    public MapObject(StackPane stackPane, MapPolygon mapPolygon, Button drowButton, Button clearButton) {
        this.stackPane = stackPane;
        this.mapPolygon = mapPolygon;
        this.droneMarkerLayer = new DroneMarkerLayer();
        this.pathLayer = new PathLayer();
        this.drowButton = drowButton;
        this.clearButton = clearButton;

        // Load background image
        Image bgImage = new Image(getClass().getResourceAsStream("/app/groundstation/terrain_world.png"));
        ImageView imageView = new ImageView(bgImage);
        imageView.setFitWidth(IMAGE_SIZE);
        imageView.setFitHeight(IMAGE_SIZE);
        imageView.setPreserveRatio(true);

        this.canvas = new Canvas(IMAGE_SIZE, IMAGE_SIZE);
        
        this.stackPane.getChildren().addAll(imageView, canvas);

        if (this.clearButton != null) {
            this.clearButton.setOnMouseClicked(event -> {
                this.mapPolygon.clear();
                redraw();
            });
        }
        if (this.drowButton != null) {
            this.drowButton.setOnMouseClicked(event -> drawSearchArea());
        }
        
        // Handle clicks on canvas
        this.canvas.setOnMouseClicked(event -> {
            double worldX = event.getX() / SCALE;
            double worldY = event.getY() / SCALE;
            mapPolygon.listenHandle(worldX, worldY);
        });
        
        redraw();
    }

    /**
     * Update drone position marker on the map. Call from JavaFX thread.
     */
    public void updateDronePosition(double x, double y) {
        droneMarkerLayer.setPosition(x, y);
        redraw();
    }

    /**
     * Update flight path polyline. Call from JavaFX thread.
     */
    public void updateDronePath(List<WorldPoint> pathPoints) {
        pathLayer.setPath(pathPoints);
        redraw();
    }

    /**
     * Hide drone marker and path (e.g. when drone disconnects).
     */
    public void clearDronePreview() {
        droneMarkerLayer.hide();
        pathLayer.clear();
        redraw();
    }

    public void drawSearchArea() {
        mapPolygon.draw();
        redraw();
    }

    public void clearSearchArea() {
        mapPolygon.clear();
        redraw();
    }

    public MapPolygon getMapPolygon() {
        return mapPolygon;
    }

    /** Center the map view on the drone position during flight. */
    public void centerOnDrone(double lat, double lon) {
        // No-op for static image as it doesn't pan.
    }

    private void redraw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, IMAGE_SIZE, IMAGE_SIZE);
        
        // Draw Polygon
        if (mapPolygon.isVisible()) {
            List<PolygonPoint> pts = mapPolygon.getPolygonPoints();
            if (pts.size() >= 3) {
                double[] xPoints = new double[pts.size()];
                double[] yPoints = new double[pts.size()];
                for (int i = 0; i < pts.size(); i++) {
                    xPoints[i] = pts.get(i).getLongitude() * SCALE; // longitude is X
                    yPoints[i] = pts.get(i).getLatitude() * SCALE;  // latitude is Y
                }
                
                gc.setFill(Color.rgb(0, 150, 255, 0.4));
                gc.setStroke(Color.BLUE);
                gc.setLineWidth(2.0);
                gc.fillPolygon(xPoints, yPoints, pts.size());
                gc.strokePolygon(xPoints, yPoints, pts.size());
            }
        }
        
        // Draw Path
        if (pathLayer.isVisible()) {
            List<WorldPoint> pts = pathLayer.getPathPoints();
            if (pts.size() > 1) {
                gc.setStroke(Color.rgb(255, 200, 50, 0.9));
                gc.setLineWidth(3.0);
                gc.beginPath();
                gc.moveTo(pts.get(0).getX() * SCALE, pts.get(0).getY() * SCALE);
                for (int i = 1; i < pts.size(); i++) {
                    gc.lineTo(pts.get(i).getX() * SCALE, pts.get(i).getY() * SCALE);
                }
                gc.stroke();
            }
        }
        
        // Draw Drone Marker
        if (droneMarkerLayer.isVisible()) {
            double cx = droneMarkerLayer.getX() * SCALE;
            double cy = droneMarkerLayer.getY() * SCALE;
            double radius = 8.0;
            
            gc.setFill(Color.rgb(255, 50, 50, 0.9));
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(2.0);
            
            gc.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
            gc.strokeOval(cx - radius, cy - radius, radius * 2, radius * 2);
        }
    }
}
