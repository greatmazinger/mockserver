package org.mockserver.model;

import java.nio.charset.Charset;

import static org.mockserver.model.MediaType.DEFAULT_HTTP_CHARACTER_SET;

/**
 * @author jamesdbloom
 */
public class StringBody extends BodyWithContentType<String> {

    public static final MediaType DEFAULT_CONTENT_TYPE = MediaType.create("text", "plain");
    private final boolean subString;
    private final String value;
    private final byte[] rawBytes;

    public StringBody(String value) {
        this(value, null, false, null);
    }

    public StringBody(String value, Charset charset) {
        this(value, null, false, (charset != null ? DEFAULT_CONTENT_TYPE.withCharset(charset) : null));
    }

    public StringBody(String value, MediaType contentType) {
        this(value, null, false, contentType);
    }

    public StringBody(String value, byte[] rawBytes, boolean subString, MediaType contentType) {
        super(Type.STRING, contentType);
        this.value = value;
        this.subString = subString;

        if (rawBytes == null && value != null) {
            this.rawBytes = value.getBytes(determineCharacterSet(contentType, DEFAULT_HTTP_CHARACTER_SET));
        } else {
            this.rawBytes = rawBytes;
        }
    }

    public static StringBody exact(String body) {
        return new StringBody(body);
    }

    public static StringBody exact(String body, Charset charset) {
        return new StringBody(body, charset);
    }

    public static StringBody exact(String body, MediaType contentType) {
        return new StringBody(body, contentType);
    }

    public static StringBody subString(String body) {
        return new StringBody(body, null, true, null);
    }

    public static StringBody subString(String body, Charset charset) {
        return new StringBody(body, null, true, (charset != null ? DEFAULT_CONTENT_TYPE.withCharset(charset) : null));
    }

    public static StringBody subString(String body, MediaType contentType) {
        return new StringBody(body, null, true, contentType);
    }

    public String getValue() {
        return value;
    }

    public byte[] getRawBytes() {
        return rawBytes;
    }

    public boolean isSubString() {
        return subString;
    }

    @Override
    public String toString() {
        return value;
    }
}
