package org.mockserver.netty.responsewriter;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.cors.CORSHeaders;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.ConnectionOptions;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAPI;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAllResponses;
import static org.mockserver.mock.HttpStateHandler.PATH_PREFIX;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;
import static org.slf4j.event.Level.DEBUG;
import static org.slf4j.event.Level.WARN;

/**
 * @author jamesdbloom
 */
public class NettyResponseWriter extends ResponseWriter {

    private final MockServerLogger mockServerLogger;
    private final ChannelHandlerContext ctx;
    private final Scheduler scheduler;
    private static final CORSHeaders CORS_HEADERS = new CORSHeaders();

    public NettyResponseWriter(MockServerLogger mockServerLogger, ChannelHandlerContext ctx, Scheduler scheduler) {
        this.mockServerLogger = mockServerLogger;
        this.ctx = ctx;
        this.scheduler = scheduler;
    }

    @Override
    public void writeResponse(final HttpRequest request, final HttpResponseStatus responseStatus) {
        writeResponse(request, responseStatus, "", "application/json");
    }

    @Override
    public void writeResponse(final HttpRequest request, final HttpResponseStatus responseStatus, final String body, final String contentType) {
        HttpResponse response = response()
            .withStatusCode(responseStatus.code())
            .withBody(body);
        if (body != null && !body.isEmpty()) {
            response.replaceHeader(header(CONTENT_TYPE.toString(), contentType + "; charset=utf-8"));
        }
        writeResponse(request, response, true);
    }

    @Override
    public void writeResponse(final HttpRequest request, HttpResponse response, final boolean apiResponse) {
        if (response == null) {
            response = notFoundResponse();
        }
        if (enableCORSForAllResponses()) {
            CORS_HEADERS.addCORSHeaders(request, response);
        } else if (apiResponse && enableCORSForAPI()) {
            CORS_HEADERS.addCORSHeaders(request, response);
        }
        if (apiResponse) {
            response.withHeader("version", org.mockserver.Version.getVersion());
            final String path = request.getPath().getValue();
            if (!path.startsWith(PATH_PREFIX) && !path.equals(ConfigurationProperties.livenessHttpGetPath())) {
                response.withHeader("deprecated",
                    "\"" + path + "\" is deprecated use \"" + PATH_PREFIX + path + "\" instead");
            }
        }

        writeAndCloseSocket(ctx, request, addConnectionHeader(request, response));
    }

    private void writeAndCloseSocket(final ChannelHandlerContext ctx, final HttpRequest request, HttpResponse response) {
        boolean closeChannel;

        ConnectionOptions connectionOptions = response.getConnectionOptions();
        if (connectionOptions != null && connectionOptions.getCloseSocket() != null) {
            closeChannel = connectionOptions.getCloseSocket();
        } else {
            closeChannel = !(request.isKeepAlive() != null && request.isKeepAlive());
        }

        ChannelFuture channelFuture = ctx.writeAndFlush(response);
        if (closeChannel || ConfigurationProperties.alwaysCloseSocketConnections()) {
            channelFuture.addListener((ChannelFutureListener) future -> {
                Delay closeSocketDelay = connectionOptions != null ? connectionOptions.getCloseSocketDelay() : null;
                if (closeSocketDelay == null) {
                    disconnectAndCloseChannel(future);
                } else {
                    scheduler.schedule(() -> disconnectAndCloseChannel(future), false, closeSocketDelay);
                }
            });
        }
    }

    private void disconnectAndCloseChannel(ChannelFuture future) {
        future
            .channel()
            .disconnect()
            .addListener(disconnectFuture -> {
                    if (disconnectFuture.isSuccess()) {
                        future
                            .channel()
                            .close()
                            .addListener(closeFuture -> {
                                if (disconnectFuture.isSuccess()) {
                                    mockServerLogger
                                        .logEvent(new LogEntry()
                                            .setLogLevel(DEBUG)
                                            .setMessageFormat("disconnected and closed socket " + future.channel().localAddress())
                                        );
                                } else {
                                    mockServerLogger
                                        .logEvent(new LogEntry()
                                            .setLogLevel(WARN)
                                            .setMessageFormat("exception closing socket " + future.channel().localAddress())
                                            .setThrowable(disconnectFuture.cause()));
                                }
                            });
                    } else {
                        mockServerLogger
                            .logEvent(new LogEntry()
                                .setLogLevel(WARN)
                                .setMessageFormat("exception disconnecting socket " + future.channel().localAddress())
                                .setThrowable(disconnectFuture.cause()));
                    }
                }
            );
    }

}
