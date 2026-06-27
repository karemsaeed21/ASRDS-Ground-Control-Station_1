package app.groundstation.map;

import java.util.List;

public class MapPolygon {

    private List<PolygonPoint> polygonPoints;
    private boolean visible;

    public MapPolygon(List<PolygonPoint> polygonPoints) {
        this.polygonPoints = polygonPoints;
        this.visible = false;
    }

    public void listenHandle(double _long, double _lat) {
        for (PolygonPoint point : polygonPoints) {
            point.CordinatesUpdate(_long, _lat);
        }
    }

    public void draw() {
        syncFromFields();
        this.visible = true;
    }

    public void syncFromFields() {
        for (PolygonPoint point : polygonPoints) {
            point.syncFromFields();
        }
    }

    public List<PolygonPoint> getPolygonPoints() {
        return polygonPoints;
    }

    public void clear() {
        this.visible = false;
    }

    public boolean isVisible() {
        return visible;
    }
}
