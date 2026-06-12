package com.qmsbrowser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class RemoteCommandAuth {
    private static final long MAX_CLOCK_SKEW_MS = 120000;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private RemoteCommandAuth() {
    }

    static boolean verify(
            String secret,
            String commandId,
            long timestamp,
            String action,
            String value,
            String suppliedSignature
    ) throws Exception {
        if (secret == null || suppliedSignature == null || suppliedSignature.isEmpty()) {
            return false;
        }
        String expectedSignature = sign(secret, commandId, timestamp, action, value);
        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.US_ASCII),
                suppliedSignature.getBytes(StandardCharsets.US_ASCII)
        );
    }

    static String sign(
            String secret,
            String commandId,
            long timestamp,
            String action,
            String value
    ) throws Exception {
        String canonical = commandId + "\n" + timestamp + "\n" + action + "\n" + value;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return toHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    static boolean isFresh(long timestamp, long now) {
        return timestamp > 0 && Math.abs(now - timestamp) <= MAX_CLOCK_SKEW_MS;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            int unsigned = value & 0xff;
            result.append(HEX[unsigned >>> 4]);
            result.append(HEX[unsigned & 0x0f]);
        }
        return result.toString();
    }
}
