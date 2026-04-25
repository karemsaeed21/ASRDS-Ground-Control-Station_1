package app.groundstation.serialCommumication.DataHandlers.DataConverters;

import app.groundstation.serialCommumication.DataHandlers.DataFormats.DroneResponse;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class ResponseJsonConverter implements Converter<DroneResponse, DroneResponse> {

    @Override
    public byte[] encode(DroneResponse data) {
        JSONObject jsonObject = data.getJsonObject();
        return jsonObject.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public DroneResponse decode(byte[] data) {
        String decoded_string = new String(data, StandardCharsets.UTF_8);
        JSONObject jsonObject = new JSONObject(decoded_string);
        DroneResponse response = new DroneResponse();
        response.deserializeJsonObject(jsonObject);
        return response;
    }
}
