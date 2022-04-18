package server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.string.StringDecoder;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Stack;

public class RefinedStringDecoder extends StringDecoder {

    private final Charset charset;
    Stack<Character> stack = new Stack<>();

    public RefinedStringDecoder() {
        this(Charset.defaultCharset());
    }

    public RefinedStringDecoder(Charset charset) {
        this.charset = charset;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
        String str = msg.toString(charset);

        stack.clear();

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);

            if (c != '\b') {
                stack.push(c);
            } else if (!stack.empty()) {
                stack.pop();
            }
        }

        StringBuilder builder = new StringBuilder(stack.size());

        for (Character c : stack) {
            builder.append(c);
        }

        out.add(builder.toString());
    }
}
