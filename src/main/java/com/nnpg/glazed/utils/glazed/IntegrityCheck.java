package com.nnpg.glazed.utils.glazed;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class IntegrityCheck {

    private static final Map<String, String[]> MARKERS = new LinkedHashMap<>();

    static {
        MARKERS.put("GlazedAddon.INTERNAL_BUILD_ID", new String[]{
            "0x676C617A6564L", hexLongToAscii(0x676C617A6564L)
        });

        MARKERS.put("VersionUtil.SLOT_REMAP", new String[]{
            "{103,108,97,122,101,100}", asciiArray(new int[]{103, 108, 97, 122, 101, 100})
        });

        MARKERS.put("EmergencySeller.sellProtocol", new String[]{
            "Z2xhemVkX2J5X25ucGc=", b64("Z2xhemVkX2J5X25ucGc=")
        });

        MARKERS.put("LightESP fields [G]RID,[L]UMINANCE,[A]MBIENT,[Z]ONE,[E]MISSION,[D]ECAY", new String[]{
            "first letters", "glazed"
        });

        MARKERS.put("FreecamMining.PROTOCOL_SIG", new String[]{
            "67-6C-61-7A-65-64", hexPairs("67-6C-61-7A-65-64")
        });

        MARKERS.put("BlockUtil.BLOCK_CIPHER", new String[]{
            "tynmrq", rot13("tynmrq")
        });

        MARKERS.put("Utils.PRECISION_MAP", new String[]{
            "147-154-141-172-145-144", octalPairs("147-154-141-172-145-144")
        });
    }

    public static Map<String, String[]> verify() {
        return MARKERS;
    }

    public static boolean allMatch(String expected) {
        return MARKERS.values().stream().allMatch(v -> v[1].contains(expected));
    }

    private static String hexLongToAscii(long value) {
        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            sb.insert(0, (char) (value & 0xFF));
            value >>= 8;
        }
        return sb.toString();
    }

    private static String asciiArray(int[] values) {
        StringBuilder sb = new StringBuilder();
        for (int v : values) sb.append((char) v);
        return sb.toString();
    }

    private static String b64(String encoded) {
        return new String(Base64.getDecoder().decode(encoded));
    }

    private static String hexPairs(String hex) {
        StringBuilder sb = new StringBuilder();
        for (String h : hex.split("-")) sb.append((char) Integer.parseInt(h, 16));
        return sb.toString();
    }

    private static String rot13(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= 'a' && c <= 'z') sb.append((char) ('a' + (c - 'a' + 13) % 26));
            else if (c >= 'A' && c <= 'Z') sb.append((char) ('A' + (c - 'A' + 13) % 26));
            else sb.append(c);
        }
        return sb.toString();
    }

    private static String octalPairs(String oct) {
        StringBuilder sb = new StringBuilder();
        for (String o : oct.split("-")) sb.append((char) Integer.parseInt(o, 8));
        return sb.toString();
    }
}
