package me.navoei.customdiscs;

import java.util.Map;

public final class NetworkProtocol {
    public static final String MOD_VERSION = "0.3.1";
    public static final String WIRE_VERSION = "3";
    public static final int PLAYBACK_MESSAGE_ID = 0;
    public static final int AUDIO_REQUEST_MESSAGE_ID = 1;
    public static final int AUDIO_FINISHED_MESSAGE_ID = 2;
    public static final int AUDIO_CHUNK_MESSAGE_ID = 3;
    public static final int MODEL_SELECT_MESSAGE_ID = 4;
    public static final int MODEL_LIST_MESSAGE_ID = 5;
    public static final int CLIENT_LANGUAGE_MESSAGE_ID = 6;
    public static final int AUDIO_STARTED_MESSAGE_ID = 7;
    private static final String MOD_ID = "customdiscs";

    private NetworkProtocol() {
    }

    public static boolean isCompatible(Map<String, String> remoteVersions) {
        return MOD_VERSION.equals(remoteVersions.get(MOD_ID));
    }

    public static String mismatchReason(String remoteVersion) {
        if (remoteVersion == null) {
            return "remote mod version is absent";
        }
        if (!MOD_VERSION.equals(remoteVersion)) {
            return "mod version mismatch (expected " + MOD_VERSION
                    + ", received " + safeVersion(remoteVersion) + ")";
        }
        return "mod version matches; wire protocol " + WIRE_VERSION
                + " is not separately advertised (compatibility is gated by exact mod version)";
    }

    public static String handshakeDiagnostic(String side, String remoteVersion) {
        boolean compatible = MOD_VERSION.equals(remoteVersion);
        return "network_check side=" + safeVersion(side)
                + " local_mod_version=" + MOD_VERSION
                + " expected_wire_protocol=" + WIRE_VERSION
                + " remote_mod_version=" + (remoteVersion == null
                ? "<absent>" : safeVersion(remoteVersion))
                + " result=" + (compatible ? "ACCEPT" : "REJECT")
                + " reason=" + mismatchReason(remoteVersion);
    }

    public static String safeVersion(String version) {
        StringBuilder safe = new StringBuilder(Math.min(version.length(), 64));
        for (int i = 0; i < version.length() && safe.length() < 64; i++) {
            char character = version.charAt(i);
            safe.append(Character.isLetterOrDigit(character)
                    || character == '.' || character == '-' || character == '_' || character == '+'
                    ? character : '?');
        }
        if (version.length() > 64) {
            safe.append("...");
        }
        return safe.toString();
    }
}
