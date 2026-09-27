package me.navoei.customdiscs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class AudioDownloader {
    private AudioDownloader() {
    }

    public static String download(String source, String requestedName) throws IOException {
        if (requestedName == null || requestedName.length() > ModConfig.maximumFilenameLength) {
            throw new IOException("Filename exceeds " + ModConfig.maximumFilenameLength + " characters.");
        }
        MusicFiles.resolve(requestedName);
        FilebinSource filebin = resolveFilebin(source);
        URL downloadUrl = filebin == null ? validateUrl(source) : filebin.resolve();
        File target = reserveUniqueTarget(requestedName);
        String finalName = target.getName();
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create directory: " + parent);
        }
        File temporary = null;
        long limit = (long) ModConfig.maximumDownloadSizeMb * 1024L * 1024L;
        HttpURLConnection connection = null;
        try {
            temporary = Files.createTempFile(parent.toPath(), "." + finalName, ".part").toFile();
            connection = (HttpURLConnection) downloadUrl.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "CustomDiscs/1.7.10");
            if (filebin != null && filebin.binListing) {
                connection.setRequestProperty("Accept", "application/json");
            }
            connection.connect();
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("Download returned HTTP " + status + ".");
            }
            long contentLength = connection.getContentLengthLong();
            if (contentLength > limit) {
                throw new IOException("File exceeds the " + ModConfig.maximumDownloadSizeMb + " MB limit.");
            }
            try (InputStream input = connection.getInputStream();
                 java.io.FileOutputStream output = new java.io.FileOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > limit) {
                        throw new IOException("File exceeds the " + ModConfig.maximumDownloadSizeMb + " MB limit.");
                    }
                    output.write(buffer, 0, count);
                }
                if (total == 0) {
                    throw new IOException("The downloaded file is empty.");
                }
            }
            moveIntoPlace(temporary, target);
            return finalName;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (temporary != null && temporary.exists() && !temporary.delete()) {
                temporary.deleteOnExit();
            }
            if (target.exists() && target.length() == 0L && !target.delete()) {
                target.deleteOnExit();
            }
        }
    }

    private static URL validateUrl(String value) throws IOException {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IOException("Only HTTP and HTTPS URLs are allowed.");
            }
            return uri.toURL();
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Invalid download URL.", e);
        }
    }

    private static FilebinSource resolveFilebin(String value) throws IOException {
        URI uri;
        try {
            uri = new URI(value);
        } catch (Exception e) {
            return null;
        }
        String host = uri.getHost();
        if (host == null || !("filebin.net".equalsIgnoreCase(host)
                || "www.filebin.net".equalsIgnoreCase(host))) {
            return null;
        }
        String[] segments = uri.getPath().split("/");
        String bin = segments.length > 1 ? segments[1] : "";
        if (bin.isEmpty()) {
            throw new IOException("Invalid Filebin URL.");
        }
        if (segments.length > 2 && !segments[2].isEmpty()) {
            return new FilebinSource(uri.toURL(), false);
        }
        try {
            URL listingUrl = new URL("https://" + host + "/" + bin);
            return new FilebinSource(listingUrl, true);
        } catch (Exception e) {
            throw new IOException("Invalid Filebin URL.", e);
        }
    }

    private static File reserveUniqueTarget(String filename) throws IOException {
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        String extension = dot > 0 ? filename.substring(dot) : "";
        for (int index = 1; index < Integer.MAX_VALUE; index++) {
            String candidate = index == 1 ? filename : base + "_" + (index - 1) + extension;
            File target = MusicFiles.resolve(candidate);
            File parent = target.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Cannot create directory: " + parent);
            }
            if (target.createNewFile()) {
                return target;
            }
        }
        throw new IOException("Unable to find an available filename.");
    }

    private static void moveIntoPlace(File temporary, File target) throws IOException {
        try {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static final class FilebinSource {
        private final URL url;
        private final boolean binListing;

        private FilebinSource(URL url, boolean binListing) {
            this.url = url;
            this.binListing = binListing;
        }

        private URL resolve() throws IOException {
            if (!binListing) {
                return url;
            }
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "CustomDiscs/1.7.10");
            try {
                if (connection.getResponseCode() != 200) {
                    throw new IOException("Filebin API returned HTTP " + connection.getResponseCode() + ".");
                }
                JsonObject response;
                try (java.io.InputStreamReader reader = new java.io.InputStreamReader(
                        connection.getInputStream(), "UTF-8")) {
                    response = new JsonParser().parse(reader).getAsJsonObject();
                }
                JsonArray files = response.getAsJsonArray("files");
                if (files == null) {
                    throw new IOException("Filebin returned no file list.");
                }
                for (JsonElement element : files) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject file = element.getAsJsonObject();
                    JsonElement type = file.get("content-type");
                    JsonElement name = file.get("filename");
                    if (type == null || name == null || !isAudioContentType(type.getAsString())) {
                        continue;
                    }
                    String encoded = new URI(null, null, name.getAsString(), null).toASCIIString();
                    return new URL(url.toExternalForm() + "/" + encoded);
                }
                throw new IOException("No WAV, MP3, or FLAC file was found in the Filebin.");
            } catch (java.net.URISyntaxException e) {
                throw new IOException("Invalid Filebin filename.", e);
            } finally {
                connection.disconnect();
            }
        }

        private static boolean isAudioContentType(String value) {
            return value.equals("audio/wav") || value.equals("audio/x-wav")
                    || value.equals("audio/mpeg") || value.equals("audio/flac")
                    || value.equals("audio/x-flac");
        }
    }
}
