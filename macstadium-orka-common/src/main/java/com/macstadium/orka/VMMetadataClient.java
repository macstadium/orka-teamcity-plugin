package com.macstadium.orka;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class VMMetadataClient {
    private static final String DEFAULT_ENDPOINT = "http://169.254.169.254/metadata";
    private static final String VALUE_KEY = "\"value\"";
    private static final int HEX_DIGITS = 4;
    // The per-attempt ceiling, which the wait budget then bounds the total on top of, so a hung
    // listener costs seconds rather than stalling agent startup indefinitely.
    private static final int TIMEOUT = 2 * 1000;

    private final String endpoint;

    public VMMetadataClient() {
        this(DEFAULT_ENDPOINT);
    }

    public VMMetadataClient(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getValue(String key) throws IOException {
        return parseValue(this.get(String.format("%s/%s", this.endpoint, key)));
    }

    /**
     * Reads the key, repeating only attempts that failed outright. A response that did not carry
     * the key is an answer rather than a gap, so it is returned as it stands: the keys this service
     * carries are settled before the VM boots, and asking again cannot change what it holds.
     * Returns null when the key was absent and when every attempt failed.
     */
    public String getValue(String key, int attempts) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                return this.getValue(key);
            } catch (IOException transientFailure) {
                // A request that threw reported nothing, which is the one case worth repeating.
            }
        }

        return null;
    }

    /**
     * Polls until the key resolves to a value or the budget runs out. orka-vm-tools brings the
     * listener up before the engine has handed it the VM's metadata, and answers 400 for every
     * key in between, so a null from a single lookup means "not yet" as often as "never".
     * Returns null when the key never resolved within the budget.
     */
    public String waitForValue(String key, long totalWaitMillis, long millisBetweenRetries)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(totalWaitMillis);
        while (true) {
            try {
                String value = this.getValue(key);
                if (value != null) {
                    return value;
                }
            } catch (IOException notYetListening) {
                // orka-vm-tools may not have brought up the link-local listener yet.
            }

            if (System.nanoTime() >= deadline) {
                return null;
            }
            Thread.sleep(millisBetweenRetries);
        }
    }

    static String parseValue(String body) {
        if (body == null) {
            return null;
        }

        // Scans on rather than surrendering at the first hit: the service echoes the key name
        // alongside the value, so a lookup of a key literally named "value" puts the token in
        // value position before it ever appears in key position.
        int index = body.indexOf(VALUE_KEY);
        while (index >= 0) {
            int after = skipWhitespace(body, index + VALUE_KEY.length());
            if (after < body.length() && body.charAt(after) == ':') {
                int start = skipWhitespace(body, after + 1);
                return start < body.length() && body.charAt(start) == '"' ? readString(body, start + 1) : null;
            }
            index = body.indexOf(VALUE_KEY, index + 1);
        }

        return null;
    }

    private static int skipWhitespace(String body, int from) {
        int index = from;
        while (index < body.length() && Character.isWhitespace(body.charAt(index))) {
            index++;
        }
        return index;
    }

    private static String readString(String body, int from) {
        StringBuilder value = new StringBuilder();
        for (int index = from; index < body.length(); index++) {
            char current = body.charAt(index);
            if (current == '"') {
                return value.toString();
            }
            if (current != '\\') {
                value.append(current);
                continue;
            }

            index++;
            if (index >= body.length()) {
                return null;
            }

            char escaped = body.charAt(index);
            switch (escaped) {
                case 'n':
                    value.append('\n');
                    break;
                case 't':
                    value.append('\t');
                    break;
                case 'r':
                    value.append('\r');
                    break;
                case 'b':
                    value.append('\b');
                    break;
                case 'f':
                    value.append('\f');
                    break;
                case 'u':
                    if (index + HEX_DIGITS >= body.length()) {
                        return null;
                    }
                    try {
                        value.append((char) Integer.parseInt(body.substring(index + 1, index + 1 + HEX_DIGITS), 16));
                    } catch (NumberFormatException malformed) {
                        return null;
                    }
                    index += HEX_DIGITS;
                    break;
                // Covers \" and \\ and \/, where the escaped character is the literal itself.
                default:
                    value.append(escaped);
                    break;
            }
        }

        // An unterminated string means a truncated body, which is not a value.
        return null;
    }

    private String get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT);
        connection.setReadTimeout(TIMEOUT);
        connection.setRequestMethod("GET");
        try {
            // orka-vm-metadata answers an unknown key with 400, which is the service's verdict on
            // the key rather than a failure to answer. Only 5xx and connection errors, which
            // getInputStream surfaces as IOException, count as no answer.
            int status = connection.getResponseCode();
            if (status >= 400 && status < 500) {
                return null;
            }
            try (InputStream stream = connection.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int read;
                while ((read = stream.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                }
                return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }
}
