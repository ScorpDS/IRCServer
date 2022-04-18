package server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.AsciiHeadersEncoder;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TelnetIRCServer {


    /**
     * ChannelGroup recipients =
     * new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
     * recipients.add(channelA);
     * recipients.add(channelB);
     *
     * @param args
     * @throws InterruptedException
     */

    private final static Map<String, String> usersCredentials = new ConcurrentHashMap<>();
    private final static Map<String, Map<String, Channel>> channelsMap = new ConcurrentHashMap<>();
    private final static Map<String, List<String>> channelsMessageBuffers = new ConcurrentHashMap<>();

    public static Map<String, String> getUsersCredentials() {
        return usersCredentials;
    }

    public static Map<String, Map<String, Channel>> getChannelsMap() {
        return channelsMap;
    }

    public static Map<String, List<String>> getChannelsMessageBuffers() {
        return channelsMessageBuffers;
    }

    public static void main(String[] args) throws InterruptedException {
        int port = 23;

        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        channelsMap.put("public", new HashMap<>());
        channelsMap.put("movies", new HashMap<>());
        channelsMessageBuffers.put("public", new ArrayList<>());
        channelsMessageBuffers.put("movies", new ArrayList<>());

        NioEventLoopGroup mainGroup = new NioEventLoopGroup();
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(mainGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()))
                                    .addLast(new RefinedStringDecoder())
                                    .addLast(new StringEncoder())
                                    .addLast(new MessageChannelHandler());
                        }

                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture f = b.bind(port).sync();

            f.channel().closeFuture().sync();
        } finally {
            mainGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

}

