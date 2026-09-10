package com.macstadium.orka;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VMMetadataClient {
    private static final String DEFAULT_ENDPOINT = "http://169.254.169.254/metadata";
    private static final Pattern VALUE_PATTERN = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]*)\"");
    private static final int TIMEOUT = 5 * 1000;

    private final String endpoint;

    public VMMetadataClient() {
        this(DEFAULT_ENDPOINT);
    }

    public VMMetadataClient(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getValue(String key) throws IOException {
        // The metadata service answers 200 even for "key not found", so the payload shape,
        // not the status code, decides whether the key resolved.
        return parseValue(this.get(String.format("%s/%s", this.endpoint, key)));
    }

    public String waitForValue(String key, int retries, int millisBetweenRetries) throws InterruptedException {
        for (int attempt = 0; attempt < retries; attempt++) {
            try {
                String value = this.getValue(key);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            } catch (IOException ignored) {
                // orka-vm-tools may not have brought up the link-local listener yet.
            }
            Thread.sleep(millisBetweenRetries);
        }

        return null;
    }

    static String parseValue(String body) {
        if (body == null) {
            return null;
        }

        Matcher matcher = VALUE_PATTERN.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT);
        connection.setReadTimeout(TIMEOUT);
        connection.setRequestMethod("GET");
        try (InputStream stream = connection.getInputStream()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }
}
