package org.mockserver.serialization.deserializers.body;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.slf4j.event.Level.TRACE;

/**
 * @author jamesdbloom
 */
public class BodyWithContentTypeDTODeserializer extends StdDeserializer<BodyWithContentTypeDTO> {

    private static final Map<String, Body.Type> fieldNameToType = new HashMap<>();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();
    private static ObjectMapper objectMapper;
    private static ObjectMapper jsonBodyObjectMapper;

    static {
        fieldNameToType.put("base64Bytes".toLowerCase(), Body.Type.BINARY);
        fieldNameToType.put("json".toLowerCase(), Body.Type.JSON);
        fieldNameToType.put("parameters".toLowerCase(), Body.Type.PARAMETERS);
        fieldNameToType.put("string".toLowerCase(), Body.Type.STRING);
        fieldNameToType.put("xml".toLowerCase(), Body.Type.XML);
    }

    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(BodyWithContentTypeDTODeserializer.class);

    public BodyWithContentTypeDTODeserializer() {
        super(BodyWithContentTypeDTO.class);
    }

    @Override
    public BodyWithContentTypeDTO deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
        JsonToken currentToken = jsonParser.getCurrentToken();
        String valueJsonValue = "";
        byte[] rawBytes = null;
        Body.Type type = null;
        boolean not = false;
        MediaType contentType = null;
        Charset charset = null;
        if (currentToken == JsonToken.START_OBJECT) {
            @SuppressWarnings("unchecked") Map<Object, Object> body = (Map<Object, Object>) ctxt.readValue(jsonParser, Map.class);
            for (Map.Entry<Object, Object> entry : body.entrySet()) {
                if (entry.getKey() instanceof String) {
                    String key = (String) entry.getKey();
                    if (key.equalsIgnoreCase("type")) {
                        try {
                            type = Body.Type.valueOf(String.valueOf(entry.getValue()));
                        } catch (IllegalArgumentException iae) {
                            MOCK_SERVER_LOGGER.logEvent(
                                new LogEntry()
                                    .setLogLevel(TRACE)
                                    .setMessageFormat("ignoring invalid value for \"type\" field of \"" + entry.getValue() + "\"")
                            );
                        }
                    }
                    if (containsIgnoreCase(key, "string", "regex", "json", "jsonSchema", "jsonPath", "xml", "xmlSchema", "xpath", "base64Bytes") && type != Body.Type.PARAMETERS) {
                        String fieldName = String.valueOf(entry.getKey()).toLowerCase();
                        if (fieldNameToType.containsKey(fieldName)) {
                            type = fieldNameToType.get(fieldName);
                        }
                        if (Map.class.isAssignableFrom(entry.getValue().getClass())) {
                            if (objectMapper == null) {
                                objectMapper = ObjectMapperFactory.createObjectMapper();
                            }
                            valueJsonValue = objectMapper.writeValueAsString(entry.getValue());
                        } else {
                            valueJsonValue = String.valueOf(entry.getValue());
                        }
                    }
                    if (containsIgnoreCase(key, "rawBytes", "base64Bytes")) {
                        if (entry.getValue() instanceof String) {
                            try {
                                rawBytes = BASE64_DECODER.decode((String) entry.getValue());
                            } catch (Throwable throwable) {
                                MOCK_SERVER_LOGGER.logEvent(
                                    new LogEntry()
                                        .setLogLevel(TRACE)
                                        .setMessageFormat("invalid base64 encoded rawBytes with value \"" + entry.getValue() + "\"")
                                );
                            }
                        }
                    }
                    if (key.equalsIgnoreCase("not")) {
                        not = Boolean.parseBoolean(String.valueOf(entry.getValue()));
                    }
                    if (key.equalsIgnoreCase("contentType")) {
                        try {
                            String mediaTypeHeader = String.valueOf(entry.getValue());
                            if (isNotBlank(mediaTypeHeader)) {
                                contentType = MediaType.parse(mediaTypeHeader);
                            }
                        } catch (IllegalArgumentException uce) {
                            MOCK_SERVER_LOGGER.logEvent(
                                new LogEntry()
                                    .setLogLevel(TRACE)
                                    .setMessageFormat("ignoring unsupported MediaType with value \"" + entry.getValue() + "\"")
                            );
                        }
                    }
                    if (key.equalsIgnoreCase("charset")) {
                        try {
                            charset = Charset.forName(String.valueOf(entry.getValue()));
                        } catch (UnsupportedCharsetException uce) {
                            MOCK_SERVER_LOGGER.logEvent(
                                new LogEntry()
                                    .setLogLevel(TRACE)
                                    .setMessageFormat("ignoring unsupported Charset with value \"" + entry.getValue() + "\"")
                            );
                        } catch (IllegalCharsetNameException icne) {
                            MOCK_SERVER_LOGGER.logEvent(
                                new LogEntry()
                                    .setLogLevel(TRACE)
                                    .setMessageFormat("ignoring invalid Charset with value \"" + entry.getValue() + "\"")
                            );
                        }
                    }
                }
            }
            if (type != null) {
                switch (type) {
                    case BINARY:
                        if (contentType != null) {
                            return new BinaryBodyDTO(new BinaryBody(rawBytes, contentType), not);
                        } else {
                            return new BinaryBodyDTO(new BinaryBody(rawBytes), not);
                        }
                    case JSON:
                        if (contentType != null) {
                            return new JsonBodyDTO(new JsonBody(valueJsonValue, rawBytes, contentType, JsonBody.DEFAULT_MATCH_TYPE), not);
                        } else if (charset != null) {
                            return new JsonBodyDTO(new JsonBody(valueJsonValue, rawBytes, JsonBody.DEFAULT_CONTENT_TYPE.withCharset(charset), JsonBody.DEFAULT_MATCH_TYPE), not);
                        } else {
                            return new JsonBodyDTO(new JsonBody(valueJsonValue, rawBytes, JsonBody.DEFAULT_CONTENT_TYPE, JsonBody.DEFAULT_MATCH_TYPE), not);
                        }
                    case STRING:
                        if (contentType != null) {
                            return new StringBodyDTO(new StringBody(valueJsonValue, rawBytes, false, contentType), not);
                        } else if (charset != null) {
                            return new StringBodyDTO(new StringBody(valueJsonValue, rawBytes, false, StringBody.DEFAULT_CONTENT_TYPE.withCharset(charset)), not);
                        } else {
                            return new StringBodyDTO(new StringBody(valueJsonValue, rawBytes, false, null), not);
                        }
                    case XML:
                        if (contentType != null) {
                            return new XmlBodyDTO(new XmlBody(valueJsonValue, rawBytes, contentType), not);
                        } else if (charset != null) {
                            return new XmlBodyDTO(new XmlBody(valueJsonValue, rawBytes, XmlBody.DEFAULT_CONTENT_TYPE.withCharset(charset)), not);
                        } else {
                            return new XmlBodyDTO(new XmlBody(valueJsonValue, rawBytes, XmlBody.DEFAULT_CONTENT_TYPE), not);
                        }
                }
            } else if (body.size() > 0) {
                if (jsonBodyObjectMapper == null) {
                    jsonBodyObjectMapper = new ObjectMapper();
                }
                return new JsonBodyDTO(new JsonBody(jsonBodyObjectMapper.writeValueAsString(body), JsonBody.DEFAULT_MATCH_TYPE), false);
            }
        } else if (currentToken == JsonToken.START_ARRAY) {
            if (jsonBodyObjectMapper == null) {
                jsonBodyObjectMapper = new ObjectMapper();
            }
            return new JsonBodyDTO(new JsonBody(jsonBodyObjectMapper.writeValueAsString(ctxt.readValue(jsonParser, List.class)), JsonBody.DEFAULT_MATCH_TYPE), false);
        } else if (currentToken == JsonToken.VALUE_STRING) {
            return new StringBodyDTO(new StringBody(jsonParser.getText()));
        }
        return null;
    }

    private boolean containsIgnoreCase(String valueToMatch, String... listOfValues) {
        for (String item : listOfValues) {
            if (item.equalsIgnoreCase(valueToMatch)) {
                return true;
            }
        }
        return false;
    }
}
