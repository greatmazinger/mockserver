package org.mockserver.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.serialization.model.VerificationDTO;
import org.mockserver.validator.jsonschema.JsonSchemaVerificationValidator;
import org.mockserver.verify.Verification;
import org.slf4j.event.Level;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.log.model.LogEntry.LogMessageType.VERIFICATION_FAILED;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.validator.jsonschema.JsonSchemaValidator.OPEN_API_SPECIFICATION_URL;

/**
 * @author jamesdbloom
 */
public class VerificationSerializer implements Serializer<Verification> {
    private final MockServerLogger mockServerLogger;
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private JsonSchemaVerificationValidator verificationValidator;

    public VerificationSerializer(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
        verificationValidator = new JsonSchemaVerificationValidator(mockServerLogger);
    }

    public String serialize(Verification verification) {
        try {
            return objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(new VerificationDTO(verification));
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception while serializing verification to JSON with value " + verification)
                    .setThrowable(e)
            );
            throw new RuntimeException("Exception while serializing verification to JSON with value " + verification, e);
        }
    }

    public Verification deserialize(String jsonVerification) {
        if (isBlank(jsonVerification)) {
            throw new IllegalArgumentException(
                "1 error:" + NEW_LINE +
                    " - a verification is required but value was \"" + jsonVerification + "\"" + NEW_LINE +
                    NEW_LINE +
                    OPEN_API_SPECIFICATION_URL
            );
        } else {
            String validationErrors = verificationValidator.isValid(jsonVerification);
            if (validationErrors.isEmpty()) {
                Verification verification = null;
                try {
                    VerificationDTO verificationDTO = objectMapper.readValue(jsonVerification, VerificationDTO.class);
                    if (verificationDTO != null) {
                        verification = verificationDTO.buildObject();
                    }
                } catch (Exception e) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(LogEntry.LogMessageType.EXCEPTION)
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("exception while parsing{}for Verification")
                            .setArguments(jsonVerification)
                            .setThrowable(e)
                    );
                    throw new RuntimeException("Exception while parsing [" + jsonVerification + "] for Verification", e);
                }
                return verification;
            } else {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(VERIFICATION_FAILED)
                        .setLogLevel(Level.INFO)
                        .setHttpRequest(request())
                        .setMessageFormat("validation failed:{}verification:{}")
                        .setArguments(validationErrors, jsonVerification)
                );
                throw new IllegalArgumentException(validationErrors);
            }
        }
    }

    @Override
    public Class<Verification> supportsType() {
        return Verification.class;
    }

}
