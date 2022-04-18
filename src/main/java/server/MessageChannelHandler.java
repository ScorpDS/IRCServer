package server;


import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static server.TelnetClientCommands.*;

class MessageChannelHandler extends SimpleChannelInboundHandler<String> {

    private final Logger logger = Logger.getLogger(TelnetIRCServer.class.getName());
    private final String GREETING = "Hi, stranger!\n\r";
    private final String COMMANDS = "Commands:\n\r/login <name> <password>\n\r/join <channel>\n\r/leave\n\r/users\n\r/channels\n\r";
    private static ChannelGroup recipients = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private static final int maxChannelSize = 10;
    public static final int channelMessagesBufferSize = 10;
    public static final String CRLF = "\n\r";

    private final Map<String, Map<String, Channel>> channelsMap = TelnetIRCServer.getChannelsMap();
    private final Map<String, List<String>> channelsMessageBuffers = TelnetIRCServer.getChannelsMessageBuffers();
    private final Map<String, String> usersCredentials = TelnetIRCServer.getUsersCredentials();

    private String currentChannel;
    private String currentUsername;

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        ctx.write(GREETING + COMMANDS + CRLF);
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {

        if (msg.isBlank() || msg.equals(CRLF)) {
            return;
        }
        msg = msg.trim();
        if (msg.charAt(0) == '/') {
            handleSlashCommand(ctx, msg);
        } else {
            handleMessage(ctx, msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        removeFromCurrentChannel();
        clearScreen(ctx);
    }

    private void handleSlashCommand(ChannelHandlerContext ctx, String msg) {
        updateClientHeader(ctx);
        String[] tokens = msg.split(" ");
        switch (tokens[0]) {
            case "/login" -> {
                handleLogin(ctx, tokens);
            }
            case "/join" -> {
                handleJoin(ctx, tokens);
            }
            case "/leave" -> {
                ctx.writeAndFlush("You just left channel " + currentChannel);
                removeFromCurrentChannel();
            }
            case "/users" -> {
                if (null != currentChannel) {
                    clearScreen(ctx);
                    channelsMap.get(currentChannel).keySet().forEach(s -> ctx.write(s + CRLF));
                    ctx.flush();
                } else {
                    ctx.writeAndFlush("You have not joined any channel yet.");
                }
            }
            case "/channels" -> {
                channelsMap.keySet().forEach(s -> ctx.write(s + CRLF));
                ctx.flush();
            }
            default -> {
                ctx.writeAndFlush("Unknown command." + CRLF);
                logger.log(Level.INFO, "Unknown command:%s".formatted(msg));
            }
        }
    }

    private void handleJoin(ChannelHandlerContext ctx, String[] tokens) {
        String channel = tokens[1];
        if (channelsMap.containsKey(channel)) {
            Map<String, Channel> channels = channelsMap.computeIfAbsent(channel, s -> new HashMap<>());
            if (channels.size() > maxChannelSize) {
                ctx.writeAndFlush("Too many users in the channel." + CRLF);
            } else {
                channels.put(currentUsername, ctx.channel());
                if (null != currentChannel) {
                    removeFromCurrentChannel();
                }
                currentChannel = channel;
                clearScreen(ctx);
                updateChannelScreen(ctx, true);

                if (!channelsMessageBuffers.get(currentChannel).isEmpty()) {
                    ctx.write(LOAD_SAVED_CURSOR_POS);
                    channelsMessageBuffers.get(currentChannel).forEach(ctx::write);
                }
                printServerNotification(ctx, "User %s joined the channel.".formatted(currentUsername) + CRLF);
            }
        } else {
            updateClientHeader(ctx);
            ctx.writeAndFlush("Channel " + channel + "is not listed, check /channels");
        }
    }

    private void handleLogin(ChannelHandlerContext ctx, String[] tokens) {
        if (tokens.length > 2 && !tokens[1].isBlank() && !tokens[2].isBlank()) {
            String login = tokens[1];
            String password = tokens[2];
            if (usersCredentials.containsKey(login)) {
                if (usersCredentials.get(login).equals(password)) {
                    updateClientHeader(ctx);
                    ctx.write("Login successful" + CRLF);
                    currentUsername = login;
                    ctx.flush();
                } else {
                    updateClientHeader(ctx);
                    ctx.writeAndFlush("Incorrect password!" + CRLF);
                }
            } else {
                usersCredentials.put(login, password);
                updateClientHeader(ctx);
                ctx.write("Registered successfully" + CRLF);
                currentUsername = login;
                ctx.flush();
            }
        } else {
            updateClientHeader(ctx);
            ctx.writeAndFlush("Login/password cannot be empty!" + CRLF);
        }
    }

    private void removeFromCurrentChannel() {
        Map<String, Channel> userChannel = channelsMap.get(currentChannel);
        if (null != currentChannel && null != userChannel) {
            userChannel.remove(currentUsername);
            currentChannel = null;
        }
    }

    private void handleMessage(ChannelHandlerContext ctx, String msg) {
        if (null == currentChannel) {
            ctx.writeAndFlush("You are not in channel." + CRLF);
            return;
        }
        String userMsg = currentUsername + ": " + msg + CRLF;
        broadcastMessageInCurrentChannel(userMsg);
    }

    private void broadcastMessageInCurrentChannel(String message) {

        if (channelsMap.containsKey(currentChannel) && null != channelsMap.get(currentChannel)) {
            for (Channel channel : channelsMap.get(currentChannel).values()) {
                channel.write(LOAD_SAVED_CURSOR_POS);
                channel.write(message);
                channel.write(SAVE_CURSOR_POS);
                channel.pipeline().get(MessageChannelHandler.class).updateChannelScreen(channel.pipeline().lastContext(), false);
            }

            List<String> channelBuff = channelsMessageBuffers.get(currentChannel);
            channelBuff.add(message);
            if (channelBuff.size() > channelMessagesBufferSize) {
                channelBuff.remove(0);
            }
        }
    }

    private void printServerNotification(ChannelHandlerContext ctx, String notificationText) {
        ctx.writeAndFlush(SET_ITALIC_MODE);
        broadcastMessageInCurrentChannel(notificationText);
    }

    private void updateClientHeader(ChannelHandlerContext ctx) {
        ctx.write(CLEAR_SCREEN); //control sequence for telnet terminal to clear a screen
        ctx.write(COMMANDS);
        if (null != currentChannel) {
            ctx.write("Channel: " + currentChannel + CRLF);
        }
        ctx.flush();
    }

    private void clearScreen(ChannelHandlerContext ctx) {
        ctx.write(CLEAR_SCREEN);
        ctx.flush();
    }

    private void updateChannelScreen(ChannelHandlerContext ctx, boolean initial) {
        if (!initial) ctx.write(SAVE_CURSOR_POS);
        ctx.write(CURSOR_HOME);
        ctx.write("Channel: " + currentChannel + CRLF);
        ctx.write(CLEAR_LINE);
        ctx.write(currentUsername + ": ");
        if (initial) {
            ctx.write(CRLF + SAVE_CURSOR_POS);
            ctx.write(MOVE_TO_LINE_AND_COL.formatted(2, currentUsername.length() + 2));
        }
        ctx.flush();
    }
}