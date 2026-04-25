package app.groundstation.mainBrain;

import com.gluonhq.maps.MapPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds current drone position and the flight path (history of positions).
 */
public class DroneState {

    private static final int MAX_PATH_POINTS = 2000;

    private double currentLat;
    private double currentLon;
    private double currentAlt;
    private int currentBattery = -1;
    private boolean hasPosition;

    private final List<MapPoint> pathPoints = new ArrayList<>();

    public void setPosition(double lat, double lon, double alt) {
        this.currentLat = lat;
        this.currentLon = lon;
        this.currentAlt = alt;
        this.hasPosition = true;
    }

    public void setBattery(int battery) {
        this.currentBattery = battery;
    }

    public int getCurrentBattery() {
        return currentBattery;
    }

    public void addPathPoint(double lat, double lon) {
        pathPoints.add(new MapPoint(lat, lon));
        if (pathPoints.size() > MAX_PATH_POINTS) {
            pathPoints.remove(0);
        }
    }

    public double getCurrentLat() {
        return currentLat;
    }

    public double getCurrentLon() {
        return currentLon;
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
    public List<MapPoint> getPathPoints() {
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
