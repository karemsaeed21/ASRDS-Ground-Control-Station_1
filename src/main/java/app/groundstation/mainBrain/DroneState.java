package app.groundstation.mainBrain;

import app.groundstation.map.WorldPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds current drone position and the flight path (history of positions).
 */
public class DroneState {

    private static final int MAX_PATH_POINTS = 2000;

    private double currentX;
    private double currentY;
    private double currentAlt;
    private int currentBattery = -1;
    private boolean hasPosition;

    private final List<WorldPoint> pathPoints = new ArrayList<>();

    public void setPosition(double x, double y, double alt) {
        this.currentX = x;
        this.currentY = y;
        this.currentAlt = alt;
        this.hasPosition = true;
    }

    public void setBattery(int battery) {
        this.currentBattery = battery;
    }

    public int getCurrentBattery() {
        return currentBattery;
    }

    public void addPathPoint(double x, double y) {
        pathPoints.add(new WorldPoint(x, y));
        if (pathPoints.size() > MAX_PATH_POINTS) {
            pathPoints.remove(0);
        }
    }

    public double getCurrentX() {
        return currentX;
    }

    public double getCurrentY() {
        return currentY;
    }

    public double getCurrentAlt() {
        return currentAlt;
    }

    public boolean hasPosition() {
        return hasPosition;
    }

    /**
     * Returns an unmodifiable copy of the path for safe use on the FX thread.
     */
    public List<WorldPoint> getPathPoints() {
        return Collections.unmodifiableList(new ArrayList<>(pathPoints));
    }

    public void clearPath() {
        pathPoints.clear();
    }

    public void clear() {
        hasPosition = false;
        currentBattery = -1;
        pathPoints.clear();
    }
}
