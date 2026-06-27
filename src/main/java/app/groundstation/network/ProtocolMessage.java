package app.groundstation.network;

import org.json.JSONObject;
import java.util.UUID;
import java.time.Instant;

public class ProtocolMessage {
    
    public static JSONObject createEnvelope(String type, JSONObject payload) {
        JSONObject envelope = new JSONObject();
        envelope.put("protocol_version", "1.0");
        envelope.put("type", type);
        envelope.put("id", UUID.randomUUID().toString());
        envelope.put("timestamp", Instant.now().toString());
        envelope.put("payload", payload);
        return envelope;
    }

    public static JSONObject createReplyEnvelope(String type, String inReplyTo, JSONObject payload) {
        JSONObject envelope = createEnvelope(type, payload);
        envelope.put("in_reply_to", inReplyTo);
        return envelope;
    }
}
