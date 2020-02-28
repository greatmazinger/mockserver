package org.mockserver.client;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import org.mockserver.codec.MockServerClientCodec;
import org.mockserver.logging.LoggingHandler;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.mockserver.socket.tls.NettySslContextFactory;

import java.net.InetSocketAddress;

import static org.mockserver.client.NettyHttpClient.REMOTE_SOCKET;
import static org.mockserver.client.NettyHttpClient.SECURE;
import static org.slf4j.event.Level.TRACE;

@ChannelHandler.Sharable
public class HttpClientInitializer extends ChannelInitializer<SocketChannel> {

    private final MockServerLogger mockServerLogger;
    private final boolean forwardProxyClient;
    private final HttpClientConnectionHandler httpClientConnectionHandler;
    private final HttpClientHandler httpClientHandler;
    private final ProxyConfiguration proxyConfiguration;
    private final NettySslContextFactory nettySslContextFactory;

    HttpClientInitializer(ProxyConfiguration proxyConfiguration, MockServerLogger mockServerLogger, boolean forwardProxyClient, NettySslContextFactory nettySslContextFactory) {
        this.proxyConfiguration = proxyConfiguration;
        this.mockServerLogger = mockServerLogger;
        this.forwardProxyClient = forwardProxyClient;
        this.httpClientHandler = new HttpClientHandler();
        this.httpClientConnectionHandler = new HttpClientConnectionHandler();
        this.nettySslContextFactory = nettySslContextFactory;
    }

    @Override
    public void initChannel(SocketChannel channel) {
        ChannelPipeline pipeline = channel.pipeline();
        boolean secure = channel.attr(SECURE) != null && channel.attr(SECURE).get() != null && channel.attr(SECURE).get();

        if (proxyConfiguration != null) {
            if (proxyConfiguration.getType() == ProxyConfiguration.Type.HTTPS && secure) {
                if (proxyConfiguration.getUsername() != null && proxyConfiguration.getPassword() != null) {
                    pipeline.addLast(new HttpProxyHandler(proxyConfiguration.getProxyAddress(), proxyConfiguration.getUsername(), proxyConfiguration.getPassword()));
                } else {
                    pipeline.addLast(new HttpProxyHandler(proxyConfiguration.getProxyAddress()));
                }
            } else if (proxyConfiguration.getType() == ProxyConfiguration.Type.SOCKS5) {
                if (proxyConfiguration.getUsername() != null && proxyConfiguration.getPassword() != null) {
                    pipeline.addLast(new Socks5ProxyHandler(proxyConfiguration.getProxyAddress(), proxyConfiguration.getUsername(), proxyConfiguration.getPassword()));
                } else {
                    pipeline.addLast(new Socks5ProxyHandler(proxyConfiguration.getProxyAddress()));
                }
            }
        }
        pipeline.addLast(httpClientConnectionHandler);

        if (secure) {
            InetSocketAddress remoteAddress = channel.attr(REMOTE_SOCKET).get();
            pipeline.addLast(nettySslContextFactory.createClientSslContext(forwardProxyClient).newHandler(channel.alloc(), remoteAddress.getHostName(), remoteAddress.getPort()));
        }

        // add logging
        if (MockServerLogger.isEnabled(TRACE)) {
            pipeline.addLast(new LoggingHandler("NettyHttpClient -->"));
        }

        pipeline.addLast(new HttpClientCodec());

        pipeline.addLast(new HttpContentDecompressor());

        pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));

        pipeline.addLast(new MockServerClientCodec(mockServerLogger));

        pipeline.addLast(httpClientHandler);
    }
}
