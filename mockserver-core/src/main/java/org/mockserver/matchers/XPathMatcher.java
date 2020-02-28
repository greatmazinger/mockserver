package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.slf4j.event.Level.*;

/**
 * @author jamesdbloom
 */
public class XPathMatcher extends BodyMatcher<String> {
    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger", "xpathExpression"};
    private final MockServerLogger mockServerLogger;
    private final String matcher;
    private final StringToXmlDocumentParser stringToXmlDocumentParser = new StringToXmlDocumentParser();
    private XPathExpression xpathExpression = null;

    XPathMatcher(MockServerLogger mockServerLogger, String matcher) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = matcher;
        if (isNotBlank(matcher)) {
            try {
                xpathExpression = XPathFactory.newInstance().newXPath().compile(matcher);
            } catch (XPathExpressionException e) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(TRACE)
                        .setMessageFormat("error while creating xpath expression for [" + matcher + "] assuming matcher not xpath - " + e.getMessage())
                        .setArguments(e)
                );
            }
        }
    }

    public boolean matches(final HttpRequest context, final String matched) {
        boolean result = false;

        if (xpathExpression == null) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(TRACE)
                    .setHttpRequest(context)
                    .setMessageFormat("attempting match against null XPath Expression for [" + matched + "]")
                    .setThrowable(new RuntimeException("Attempting match against null XPath Expression for [" + matched + "]"))
            );
        } else if (matcher.equals(matched)) {
            result = true;
        } else if (matched != null) {
            try {
                result = (Boolean) xpathExpression.evaluate(stringToXmlDocumentParser.buildDocument(matched, new StringToXmlDocumentParser.ErrorLogger() {
                    @Override
                    public void logError(final String matched, final Exception exception) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(WARN)
                                .setHttpRequest(context)
                                .setMessageFormat("SAXParseException while performing match between [" + matcher + "] and [" + matched + "]")
                                .setArguments(exception)
                        );
                    }
                }), XPathConstants.BOOLEAN);
            } catch (Exception e) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(TRACE)
                        .setHttpRequest(context)
                        .setMessageFormat("error while matching xpath [" + matcher + "] against string [" + matched + "] assuming no match - " + e.getMessage())
                );
            }
        }

        if (!result) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(DEBUG)
                    .setMessageFormat("failed to perform xpath match{}with{}")
                    .setArguments(matched, this.matcher)
            );
        }

        return not != result;
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }
}
