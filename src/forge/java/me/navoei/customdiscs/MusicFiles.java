package me.navoei.customdiscs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class MusicFiles {
    private static File musicDirectory;
    private static File dataDirectory;

    private MusicFiles() {
    }

    public static void initialize(File configDirectory) {
        dataDirectory = new File(configDirectory, "customdiscs");
        musicDirectory = new File(dataDirectory, "musicdata");
        if (!musicDirectory.isDirectory() && !musicDirectory.mkdirs()) {
            throw new IllegalStateException("Cannot create CustomDiscs musicdata directory: " + musicDirectory);
        }
    }

    public static File getMusicDirectory() {
        return musicDirectory;
    }

    public static File getDataDirectory() {
        return dataDirectory;
    }

    public static File resolve(String filename) throws IOException {
        if (filename == null || filename.trim().isEmpty() || filename.indexOf('\0') >= 0) {
            throw new IOException("Invalid audio filename.");
        }
        Path root = musicDirectory.toPath().toAbsolutePath().normalize();
        Path target = root.resolve(filename).toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            throw new IOException("Audio filename escapes the musicdata directory.");
        }
        int depth = root.relativize(target).getNameCount() - 1;
        if ("none".equals(ModConfig.subdirectoryDepth) && depth != 0) {
            throw new IOException("Subdirectories are disabled.");
        }
        if ("single".equals(ModConfig.subdirectoryDepth) && depth > 1) {
            throw new IOException("Only one subdirectory level is allowed.");
        }
        String extension = extension(target.getFileName().toString());
        if (!"wav".equalsIgnoreCase(extension) && !"mp3".equalsIgnoreCase(extension)
                && !"flac".equalsIgnoreCase(extension)) {
            throw new IOException("Audio must be WAV, MP3, or FLAC.");
        }
        return target.toFile();
    }

    public static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            java.io.InputStream input = new java.io.FileInputStream(file);
            try {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            } finally {
                input.close();
            }
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    public static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1);
    }
}
