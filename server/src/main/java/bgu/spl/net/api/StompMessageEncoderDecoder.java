package bgu.spl.net.api;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class StompMessageEncoderDecoder implements MessageEncoderDecoder<String> {
    private byte[] bytes = new byte[1 << 10]; // מתחילים עם באפר של 1KB
    private int len = 0;
    @Override
    public String decodeNextByte(byte nextByte) {
        if (nextByte == '\u0000') {
            return popString();
        }

        pushByte(nextByte);
        return null; // ההודעה עדיין לא הסתיימה
    }

    @Override
    public byte[] encode(String message) {
        return (message + "\u0000").getBytes();
    }
    private void pushByte(byte nextByte) {
        if (len >= bytes.length) {
            // אם הבאפר התמלא, מכפילים את הגודל שלו
            bytes = Arrays.copyOf(bytes, len * 2);
        }

        bytes[len++] = nextByte;
    }

    private String popString() {
        // ממירים את הבתים שצברנו למחרוזת UTF-8
        String result = new String(bytes, 0, len, StandardCharsets.UTF_8);
        len = 0; // מאפסים את המונה להודעה הבאה
        return result;
    }
}
