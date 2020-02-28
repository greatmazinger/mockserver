package org.mockserver.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.MockServerMatcher;
import org.mockserver.serialization.serializers.response.TimeToLiveSerializer;
import org.mockserver.ui.MockServerMatcherListener;
import org.mockserver.ui.MockServerMatcherNotifier;
import org.slf4j.event.Level;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockserver.serialization.ObjectMapperFactory.createObjectMapper;
import static org.slf4j.event.Level.*;

public class ExpectationFileSystemPersistence implements MockServerMatcherListener {

    private final ObjectMapper objectMapper;
    private final MockServerLogger mockServerLogger;
    private final Path filePath;
    private final boolean initializationPathMatchesPersistencePath;
    private final ReentrantLock fileWriteLock = new ReentrantLock();
    private final MockServerMatcher mockServerMatcher;

    public ExpectationFileSystemPersistence(MockServerLogger mockServerLogger, MockServerMatcher mockServerMatcher) {
        if (ConfigurationProperties.persistExpectations()) {
            this.mockServerLogger = mockServerLogger;
            this.mockServerMatcher = mockServerMatcher;
            this.objectMapper = createObjectMapper(new TimeToLiveSerializer());
            this.filePath = Paths.get(ConfigurationProperties.persistedExpectationsPath());
            try {
                Files.createFile(filePath);
            } catch (FileAlreadyExistsException ignore) {
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.EXCEPTION)
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception creating persisted expectations file " + filePath.toString())
                        .setThrowable(throwable)
                );
            }
            this.initializationPathMatchesPersistencePath = ConfigurationProperties.initializationJsonPath().equals(ConfigurationProperties.persistedExpectationsPath());
            mockServerMatcher.registerListener(this);
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(INFO)
                    .setMessageFormat("created expectation file system persistence for{}")
                    .setArguments(ConfigurationProperties.persistedExpectationsPath())
            );
        } else {
            this.mockServerLogger = null;
            this.mockServerMatcher = null;
            this.objectMapper = null;
            this.filePath = null;
            this.initializationPathMatchesPersistencePath = true;
        }
    }

    @Override
    public void updated(MockServerMatcher mockServerLog, MockServerMatcherNotifier.Cause cause) {
        // ignore non-API changes from the same file
        if (cause == MockServerMatcherNotifier.Cause.API || !initializationPathMatchesPersistencePath) {
            fileWriteLock.lock();
            try {
                try {
                    try (
                        FileOutputStream fileOutputStream = new FileOutputStream(filePath.toFile());
                        FileChannel fileChannel = fileOutputStream.getChannel();
                        FileLock fileLock = fileChannel.lock()
                    ) {
                        if (fileLock != null) {
                            List<Expectation> expectations = mockServerLog.retrieveActiveExpectations(null);
                            if (MockServerLogger.isEnabled(TRACE)) {
                                mockServerLogger.logEvent(
                                    new LogEntry()
                                        .setLogLevel(TRACE)
                                        .setMessageFormat("persisting expectations{}to{}")
                                        .setArguments(expectations, ConfigurationProperties.initializationJsonPath())
                                );
                            } else if (MockServerLogger.isEnabled(DEBUG)) {
                                mockServerLogger.logEvent(
                                    new LogEntry()
                                        .setLogLevel(DEBUG)
                                        .setMessageFormat("persisting expectations to{}")
                                        .setArguments(ConfigurationProperties.initializationJsonPath())
                                );
                            }
                            byte[] data = serialize(expectations).getBytes(UTF_8);
                            ByteBuffer buffer = ByteBuffer.wrap(data);
                            buffer.put(data);
                            buffer.flip();
                            while (buffer.hasRemaining()) {
                                fileChannel.write(buffer);
                            }
                        }
                    }
                } catch (Throwable throwable) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(LogEntry.LogMessageType.EXCEPTION)
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("exception while persisting expectations to " + filePath.toString())
                            .setThrowable(throwable)
                    );
                }
            } finally {
                fileWriteLock.unlock();
            }
        }
    }

    public String serialize(List<Expectation> expectations) {
        return serialize(expectations.toArray(new Expectation[0]));
    }

    public String serialize(Expectation... expectations) {
        try {
            if (expectations != null && expectations.length > 0) {
                return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(expectations);
            } else {
                return "[]";
            }
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception while serializing expectation to JSON with value " + Arrays.asList(expectations))
                    .setThrowable(e)
            );
            throw new RuntimeException("Exception while serializing expectation to JSON with value " + Arrays.asList(expectations), e);
        }
    }

    public void stop() {
        if (mockServerMatcher != null) {
            mockServerMatcher.unregisterListener(this);
        }
    }
}
