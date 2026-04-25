package app.groundstation.serialCommumication.DataHandlers.DataConverters;

public interface Converter<Request, Response> {

    byte[] encode(Request data);

    Response decode(byte[] data);

}
