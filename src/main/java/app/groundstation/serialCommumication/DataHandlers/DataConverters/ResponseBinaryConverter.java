package app.groundstation.serialCommumication.DataHandlers.DataConverters;

import app.groundstation.serialCommumication.DataHandlers.DataFormats.DroneResponse;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Binary codec for DroneResponse.
 *
 * Wire format (28 bytes, big-endian):
 *   [0-7]   double  lat
 *   [8-15]  double  lon
 *   [16-23] double  altitude
 *   [24-27] int     battery (%)
 */
public class ResponseBinaryConverter implements Converter<DroneResponse, DroneResponse> {

    private static final int FRAME_SIZE = 28;

    @Override
    public byte[] encode(DroneResponse data) {
        ByteBuffer buf = ByteBuffer.allocate(FRAME_SIZE).order(ByteOrder.BIG_ENDIAN);
        buf.putDouble(data.getLat());
        buf.putDouble(data.getLon());
        buf.putDouble(data.getAltitude());
        buf.putInt(data.getBattery());
        return buf.array();
    }

    @Override
    public DroneResponse decode(byte[] data) {
        if (data == null || data.length < FRAME_SIZE) return null;
        ByteBuffer buf = ByteBuffer.wrap(data, 0, FRAME_SIZE).order(ByteOrder.BIG_ENDIAN);
        DroneResponse r = new DroneResponse();
        r.setLat(buf.getDouble());
        r.setLon(buf.getDouble());
        r.setAltitude(buf.getDouble());
        r.setBattery(buf.getInt());
        return r;
    }
}
