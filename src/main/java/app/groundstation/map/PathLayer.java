package app.groundstation.map;

import java.util.ArrayList;
import java.util.List;

/**
 * Data layer that stores the flight path.
 */
public class PathLayer {

    private final List<WorldPoint> pathPoints = new ArrayList<>();
    private boolean visible;

    public PathLayer() {
        this.visible = false;
    }

    /**
     * Set the path points. Pass a copy if from another thread.
     */
    public void setPath(List<WorldPoint> points) {
        this.pathPoints.clear();
        if (points != null) {
            this.pathPoints.addAll(points);
        }
        this.visible = !pathPoints.isEmpty();
    }

    /**
     * Clear the path (e.g. when drone disconnects or user clears).
     */
    public void clear() {
        this.pathPoints.clear();
        this.visible = false;
    }

    public List<WorldPoint> getPathPoints() {
        return pathPoints;
    }

    public boolean isVisible() {
        return visible;
    }
}
