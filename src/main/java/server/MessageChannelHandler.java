package server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static server.TelnetClientCommands.*;

class MessageChannelHandler extends SimpleChannelInboundHandler<String> {

    private final Logger logger = Logger.getLogger(TelnetIRCServer.class.getName());

    private static final int maxChannelSize = 10;
    private static final int channelMessagesBufferSize = 10;

    private static final String COMMANDS =
            "Commands:\n\r/login <name> <password>\n\r/join <channel>\n\r/leave\n\r/users\n\r/channels\n\r";
    private static final String CRLF = "\n\r";

    private final Map<String, Map<String, Channel>> channelsMap = TelnetIRCServer.getChannelsMap();
    private final Map<String, List<String>> channelsMessageBuffers = TelnetIRCServer.getChannelsMessageBuffers();
    private final Map<String, String> usersCredentials = TelnetIRCServer.getUsersCredentials();

    private ScreenState screenState;

    private String currentChannel;
    private String currentUsername;

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        ctx.write(COMMANDS + CRLF);
        ctx.flush();
        screenState = ScreenState.CONNECTED;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        msg = msg.trim();

        if (handleInfoScreenInput(ctx)) return;

        if (msg.isBlank() || msg.equals(CRLF)) {
            return;
        }

        if (msg.charAt(0) == '/') {
            handleSlashCommand(ctx, msg);
        } else {
            handleMessage(ctx, msg);
        }
        ctx.flush();
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

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        removeFromCurrentChannel();
        clearScreen(ctx);
    }

    private boolean handleInfoScreenInput(ChannelHandlerContext ctx) {
        if (screenState.equals(ScreenState.USERS_INFO) || screenState.equals(ScreenState.CHANNELS_INFO)) {
            if (null != currentChannel) {
                updateChannelScreen(ctx, true);
                screenState = ScreenState.JOINED;
            } else if (null != currentUsername) {
                updateClientHeader(ctx);
                screenState = ScreenState.LOGGED_IN;
            } else {
                updateClientHeader(ctx);
                screenState = ScreenState.CONNECTED;
            }
            ctx.flush();
            return true;
        }
        return false;
    }

    private void handleSlashCommand(ChannelHandlerContext ctx, String msg) {
        String[] tokens = msg.split(" ");
        switch (tokens[0]) {
            case "/login" -> {
                if (screenState.equals(ScreenState.CONNECTED)) {
                    handleLogin(ctx, tokens);
                } else {
                    ctx.write("You already logged in." + CRLF);
                }
            }
            case "/join" -> {
                handleJoin(ctx, tokens);
            }
            case "/leave" -> {
                handleLeave(ctx);
            }
            case "/users" -> {
                if (null != currentChannel) {
                    handleInfo(ctx, channelsMap.get(currentChannel).keySet());
                    screenState = ScreenState.USERS_INFO;
                } else {
                    ctx.write("You have not joined any channel yet." + CRLF);
                }
            }
            case "/channels" -> {
                handleInfo(ctx, channelsMap.keySet());
                screenState = ScreenState.CHANNELS_INFO;
            }
            default -> {
                handleLocalMessage(ctx, "Unknown command." + CRLF);
                logger.log(INFO, "Unknown command:%s".formatted(msg));
            }
        }

    }

    private void handleLocalMessage(ChannelHandlerContext ctx, String localMessage) {
        if (screenState.equals(ScreenState.JOINED)) {
            ctx.write(LOAD_SAVED_CURSOR_POS);
            ctx.write(localMessage);
            ctx.write(SAVE_CURSOR_POS);
            updateChannelScreen(ctx, false);
        } else {
            ctx.write(localMessage);
        }
        ctx.flush();
    }

    private void handleInfo(ChannelHandlerContext ctx, Collection<String> infoList) {
        clearScreen(ctx);
        infoList.forEach(s -> ctx.write(s + CRLF));
    }

    private void handleLeave(ChannelHandlerContext ctx) {
        if (null != currentChannel) {
            broadcastMessageInCurrentChattingChannel("User %s has left the channel".formatted(currentUsername) + CRLF);
            removeFromCurrentChannel();
        }
        ctx.channel().close();
    }

    private void handleJoin(ChannelHandlerContext ctx, String[] tokens) {
        if (!(screenState.equals(ScreenState.LOGGED_IN) || screenState.equals(ScreenState.JOINED))) {
            ctx.write("You must login in order to be able to join any channel" + CRLF);
            return;
        }
        if (tokens.length < 2) {
            ctx.write("You must specify channel you want to join");
            return;
        }
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

                screenState = ScreenState.JOINED;
                printServerNotification(ctx, "User %s has joined the channel.".formatted(currentUsername) + CRLF);
            }
        } else {
            updateClientHeader(ctx);
            ctx.writeAndFlush("Channel " + channel + "is not listed, check /channels");
        }
    }

    private void printMessagesFromChannelBufferFromSavedCursorPosition(ChannelHandlerContext ctx) {
        if (!channelsMessageBuffers.get(currentChannel).isEmpty()) {
            ctx.write(LOAD_SAVED_CURSOR_POS);
            channelsMessageBuffers.get(currentChannel).forEach(ctx::write);
            ctx.write(SAVE_CURSOR_POS);
        }
    }

    private void handleLogin(ChannelHandlerContext ctx, String[] tokens) {
        if (tokens.length > 2 && !tokens[1].isBlank() && !tokens[2].isBlank()) {
            String login = tokens[1];
            String password = tokens[2];

            updateClientHeader(ctx);

            if (usersCredentials.containsKey(login)) {
                if (usersCredentials.get(login).equals(password)) {

                    ctx.write("Login successful" + CRLF);
                    currentUsername = login;
                    screenState = ScreenState.LOGGED_IN;
                } else {
                    ctx.write("Incorrect password!" + CRLF);
                }
            } else {
                usersCredentials.put(login, password);
                ctx.write("Registered successfully" + CRLF);
                currentUsername = login;
                ctx.flush();
                screenState = ScreenState.LOGGED_IN;
            }
        } else {
            ctx.writeAndFlush("Login/password cannot be empty!" + CRLF);
        }
    }

    private void removeFromCurrentChannel() {

        if (null != currentChannel) {
            Map<String, Channel> userChannel = channelsMap.get(currentChannel);
            if (null != userChannel) {
                userChannel.remove(currentUsername);
            }
            currentChannel = null;
        }
    }

    private void handleMessage(ChannelHandlerContext ctx, String msg) {
        if (null == currentChannel) {
            ctx.writeAndFlush("You are not in channel." + CRLF);
            return;
        }
        String userMsg = currentUsername + ": " + msg + CRLF;
        broadcastMessageInCurrentChattingChannel(userMsg);
    }

    private void broadcastMessageInCurrentChattingChannel(String message) {

        if (channelsMap.containsKey(currentChannel) && null != channelsMap.get(currentChannel)) {
            for (Channel channel : channelsMap.get(currentChannel).values()) {
                channel.write(LOAD_SAVED_CURSOR_POS);
                channel.write(message);
                channel.write(SAVE_CURSOR_POS);
                channel.pipeline().get(MessageChannelHandler.class)
                        .updateChannelScreen(channel.pipeline().context(MessageChannelHandler.class), false);
            }

            addMessageToChannelMessageBuffer(message);
        }
    }

    private void addMessageToChannelMessageBuffer(String message) {
        List<String> channelBuff = channelsMessageBuffers.get(currentChannel);
        channelBuff.add(message);
        if (channelBuff.size() > channelMessagesBufferSize) {
            channelBuff.remove(0);
        }
    }

    private void printServerNotification(ChannelHandlerContext ctx, String notificationText) {
        broadcastMessageInCurrentChattingChannel(notificationText);
    }

    private void updateClientHeader(ChannelHandlerContext ctx) {
        ctx.write(CLEAR_SCREEN); //control sequence for telnet terminal to clear a screen
        ctx.write(COMMANDS);
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
            printMessagesFromChannelBufferFromSavedCursorPosition(ctx);
            ctx.write(MOVE_TO_LINE_AND_COL.formatted(2, currentUsername.length() + 3));
        }
        ctx.flush();
    }
}