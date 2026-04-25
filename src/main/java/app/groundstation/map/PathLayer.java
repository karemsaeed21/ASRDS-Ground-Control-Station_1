package app.groundstation.map;

import com.gluonhq.maps.MapLayer;
import com.gluonhq.maps.MapPoint;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Map layer that draws a polyline through a list of points (flight path).
 */
public class PathLayer extends MapLayer {

    private final List<MapPoint> pathPoints = new ArrayList<>();
    private final Path path;

    public PathLayer() {
        this.path = new Path();
        this.path.setStroke(Color.rgb(255, 200, 50, 0.9));
        this.path.setStrokeWidth(3.0);
        this.path.setFill(null);
        this.path.setVisible(false);
        this.getChildren().add(path);
    }

    /**
     * Set the path points and redraw. Pass a copy if from another thread.
     */
    public void setPath(List<MapPoint> points) {
        this.pathPoints.clear();
        if (points != null) {
            this.pathPoints.addAll(points);
        }
        this.path.setVisible(!pathPoints.isEmpty());
        this.markDirty();
    }

    /**
     * Clear the path (e.g. when drone disconnects or user clears).
     */
    public void clear() {
        this.pathPoints.clear();
        this.path.setVisible(false);
        this.markDirty();
    }

    @Override
    protected void layoutLayer() {
        if (pathPoints.isEmpty()) {
            return;
        }
        List<PathElement> elements = new ArrayList<>();
        for (int i = 0; i < pathPoints.size(); i++) {
            MapPoint p = pathPoints.get(i);
            Point2D screen = this.getMapPoint(p.getLatitude(), p.getLongitude());
            if (i == 0) {
                elements.add(new MoveTo(screen.getX(), screen.getY()));
            } else {
                elements.add(new LineTo(screen.getX(), screen.getY()));
            }
        }
        path.getElements().setAll(elements);
    }
}
