package org.mockserver.dashboard.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.log.model.LogEntry;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.ObjectWithJsonToString;
import org.slf4j.event.Level;

import static org.mockserver.model.HttpRequest.request;

public class DashboardLogEntryDTO extends ObjectWithJsonToString {

    private static final String[] EXCLUDED_FIELDS = {
        "id",
        "timestamp",
        "message",
        "throwable"
    };
    private String id;
    private Level logLevel;
    private long epochTime;
    private String timestamp;
    private LogEntry.LogMessageType type;
    private HttpRequest[] httpRequests;
    private HttpResponse httpResponse;
    private HttpError httpError;
    private Expectation expectation;
    private Throwable throwable;

    private String messageFormat;
    private Object[] arguments;
    private String message;

    public DashboardLogEntryDTO(LogEntry logEntry) {
        setId(logEntry.id());
        setLogLevel(logEntry.getLogLevel());
        setTimestamp(logEntry.getTimestamp());
        setEpochTime(logEntry.getEpochTime());
        setType(logEntry.getType());
        setHttpRequests(logEntry.getHttpUpdatedRequests());
        setHttpResponse(logEntry.getHttpUpdatedResponse());
        setHttpError(logEntry.getHttpError());
        setExpectation(logEntry.getExpectation());
        setMessageFormat(logEntry.getMessageFormat());
        setArguments(logEntry.getArguments());
        setMessage(logEntry.getMessage());
        setThrowable(logEntry.getThrowable());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Level getLogLevel() {
        return logLevel;
    }

    public DashboardLogEntryDTO setLogLevel(Level logLevel) {
        this.logLevel = logLevel;
        return this;
    }

    public long getEpochTime() {
        return epochTime;
    }

    public DashboardLogEntryDTO setEpochTime(long epochTime) {
        this.epochTime = epochTime;
        return this;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public LogEntry.LogMessageType getType() {
        return type;
    }

    public DashboardLogEntryDTO setType(LogEntry.LogMessageType type) {
        this.type = type;
        return this;
    }

    @JsonIgnore
    public HttpRequest[] getHttpRequests() {
        return httpRequests;
    }

    public DashboardLogEntryDTO setHttpRequests(HttpRequest[] httpRequests) {
        this.httpRequests = httpRequests;
        return this;
    }

    public DashboardLogEntryDTO setHttpRequest(HttpRequest httpRequest) {
        if (httpRequest != null) {
            this.httpRequests = new HttpRequest[]{httpRequest};
        } else {
            this.httpRequests = new HttpRequest[]{request()};
        }
        return this;
    }

    public HttpRequest getHttpRequest() {
        if (httpRequests != null && httpRequests.length > 0) {
            return httpRequests[0];
        } else {
            return null;
        }
    }

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    public DashboardLogEntryDTO setHttpResponse(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
        return this;
    }

    public HttpError getHttpError() {
        return httpError;
    }

    public DashboardLogEntryDTO setHttpError(HttpError httpError) {
        this.httpError = httpError;
        return this;
    }

    public Expectation getExpectation() {
        return expectation;
    }

    public DashboardLogEntryDTO setExpectation(Expectation expectation) {
        this.expectation = expectation;
        return this;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public DashboardLogEntryDTO setThrowable(Throwable throwable) {
        this.throwable = throwable;
        return this;
    }

    public String getMessageFormat() {
        return messageFormat;
    }

    public DashboardLogEntryDTO setMessageFormat(String messageFormat) {
        this.messageFormat = messageFormat;
        return this;
    }

    public Object[] getArguments() {
        return arguments;
    }

    public DashboardLogEntryDTO setArguments(Object... arguments) {
        this.arguments = arguments;
        return this;
    }

    @JsonIgnore
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }
}
