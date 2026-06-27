package app.groundstation.map;

/**
 * Data layer that stores the current drone position.
 */
public class DroneMarkerLayer {

    private double x;
    private double y;
    private boolean visible;

    public DroneMarkerLayer() {
        this.visible = false;
    }

    /**
     * Set the drone position and show the marker.
     */
    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
        this.visible = true;
    }

    /**
     * Hide the marker (e.g. when drone disconnects).
     */
    public void hide() {
        this.visible = false;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public boolean isVisible() { return visible; }
}
