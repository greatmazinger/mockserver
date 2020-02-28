package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import static org.slf4j.event.Level.DEBUG;

/**
 * @author jamesdbloom
 */
public class BooleanMatcher extends ObjectWithReflectiveEqualsHashCodeToString implements Matcher<Boolean> {
    private static final String[] excludedFields = {"mockServerLogger"};
    private final MockServerLogger mockServerLogger;
    private final Boolean matcher;

    BooleanMatcher(MockServerLogger mockServerLogger, Boolean matcher) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = matcher;
    }

    @Override
    public boolean matches(final HttpRequest context, Boolean matched) {
        boolean result = false;

        if (matcher == null) {
            result = true;
        } else if (matched != null) {
            result = matched == matcher;
        }

        if (!result) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(DEBUG)
                    .setHttpRequest(context)
                    .setMessageFormat("failed to perform boolean match of{}with{}")
                    .setArguments(matched, this.matcher)
            );
        }

        return result;
    }

    @Override
    @JsonIgnore
    public String[] fieldsExcludedFromEqualsAndHashCode() {
        return excludedFields;
    }

}
