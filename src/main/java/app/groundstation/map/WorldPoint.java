package app.groundstation.map;

/**
 * A point in the 2-D world coordinate system (0–100 on both axes).
 * Replaces com.gluonhq.maps.MapPoint throughout the application.
 */
public class WorldPoint {

    private final double x;
    private final double y;

    public WorldPoint(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /** X coordinate in the world (0–100). */
    public double getX() {
        return x;
    }

    /** Y coordinate in the world (0–100). */
    public double getY() {
        return y;
    }

    @Override
    public String toString() {
        return String.format("WorldPoint[x=%.4f, y=%.4f]", x, y);
    }
}
