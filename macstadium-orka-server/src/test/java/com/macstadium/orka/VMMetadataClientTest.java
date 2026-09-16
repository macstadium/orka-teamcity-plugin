package com.macstadium.orka;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.Test;

@Test
public class VMMetadataClientTest {
    public void when_parse_value_should_return_the_value() {
        assertEquals("vm-abc123", VMMetadataClient.parseValue("{\"value\":\"vm-abc123\"}"));
    }

    public void when_parse_value_with_whitespace_should_return_the_value() {
        assertEquals("vm-abc123", VMMetadataClient.parseValue("{\"value\" : \"vm-abc123\"}"));
    }

    public void when_parse_error_response_should_return_null() {
        // The metadata service answers 200 with this body when the key is unknown.
        assertNull(VMMetadataClient.parseValue("{\"error\":\"Unable to find metadata value by provided key x\"}"));
    }

    public void when_parse_empty_value_should_return_empty() {
        assertEquals("", VMMetadataClient.parseValue("{\"value\":\"\"}"));
    }

    public void when_parse_null_should_return_null() {
        assertNull(VMMetadataClient.parseValue(null));
    }

    public void when_parse_value_with_an_escaped_quote_should_keep_the_whole_value() {
        // A VM name is unlikely to contain a quote, but truncating at one silently registers
        // the agent under a prefix of its instance id, which looks like no agent at all.
        assertEquals("vm-\"abc\"-123", VMMetadataClient.parseValue("{\"value\":\"vm-\\\"abc\\\"-123\"}"));
    }

    public void when_parse_value_with_an_escaped_backslash_should_keep_the_whole_value() {
        assertEquals("vm\\abc", VMMetadataClient.parseValue("{\"value\":\"vm\\\\abc\"}"));
    }

    public void when_parse_value_with_a_unicode_escape_should_decode_it() {
        assertEquals("vm-A", VMMetadataClient.parseValue("{\"value\":\"vm-\\u0041\"}"));
    }

    public void when_parse_multiple_values_should_return_the_first() {
        // getValue asks for one key at a time, so the first value is the answer to that key.
        assertEquals("first", VMMetadataClient.parseValue("{\"value\":\"first\",\"value\":\"second\"}"));
    }

    public void when_parse_value_after_another_key_should_skip_the_other_key() {
        assertEquals("vm-abc123",
                VMMetadataClient.parseValue("{\"key\":\"orka_vm_name\",\"value\":\"vm-abc123\"}"));
    }

    public void when_value_appears_before_the_real_key_should_still_find_it() {
        // The response to a lookup of a key named "value" looks exactly like this. Surrendering at
        // the first occurrence returns null, and a null here means the agent logs "Stopping
        // initialization" and never registers.
        assertEquals("vm-abc123",
                VMMetadataClient.parseValue("{\"key\":\"value\",\"value\":\"vm-abc123\"}"));
    }

    public void when_parse_unterminated_value_should_return_null() {
        assertNull(VMMetadataClient.parseValue("{\"value\":\"vm-abc"));
    }

    public void when_the_listener_hangs_should_give_up_on_the_per_attempt_timeout() throws Exception {
        HttpServer server = this.startServer(exchange -> {
            try {
                // Stalls well past the read timeout and then answers. A client that honours the
                // timeout has already given up and sees null; one that does not eventually gets
                // this value and fails the assertion below. Answering rather than hanging forever
                // is deliberate: it makes a dropped timeout fail the suite in seconds instead of
                // blocking the test task indefinitely.
                Thread.sleep(6 * 1000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            this.respond(exchange, 200, "{\"value\":\"vm-abc123\"}");
        });

        try {
            // A 1ms budget buys exactly one attempt, so this measures the per-request ceiling and
            // nothing else. Without it the budget never gets to fire: the clock is only read
            // between attempts, so a read that never returns blocks the loop forever.
            long started = System.nanoTime();
            assertNull(new VMMetadataClient(this.endpointOf(server))
                    .waitForFirstResponse("orka_vm_name", 1, 1));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(String.format("waited %dms, expected the read timeout to cap it", elapsedMillis),
                    elapsedMillis < 5 * 1000);
        } finally {
            server.stop(0);
        }
    }

    public void when_the_endpoint_is_unreachable_should_return_null() throws Exception {
        // A VM whose image was never prepped for daemon mode: nothing listening, no exception out.
        assertNull(new VMMetadataClient("http://127.0.0.1:1/metadata")
                .waitForFirstResponse("orka_vm_name", 200, 1));
    }

    public void when_the_daemon_key_is_present_should_return_it() throws Exception {
        // The positive statement the agent keys off: only a daemon-mode deploy attaches this key,
        // so a value here is what makes the agent configure its identity from the service.
        HttpServer server = this.startServer(exchange -> this.respond(exchange, 200, "{\"value\":\"image-abc123\"}"));

        try {
            assertEquals("image-abc123", new VMMetadataClient(this.endpointOf(server))
                    .waitForFirstResponse("teamcity_image_id", 5000, 200));
        } finally {
            server.stop(0);
        }
    }

    @Test(timeOut = 30000)
    public void when_the_service_answers_without_the_daemon_key_should_not_wait_any_further() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = this.startServer(exchange -> {
            calls.incrementAndGet();
            this.respond(exchange, 200, "{\"error\":\"Unable to find metadata value by provided key x\"}");
        });

        try {
            // An SSH-mode VM on a host whose metadata service is up. The service has spoken and it
            // did not name a TeamCity image, which settles the mode: the agent takes the local file
            // path from here. Polling on instead would charge every SSH-mode boot the full budget
            // waiting for a key that is never coming.
            long started = System.nanoTime();
            assertNull(new VMMetadataClient(this.endpointOf(server))
                    .waitForFirstResponse("teamcity_image_id", 5000, 2000));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertEquals("expected a single request once the service answered", 1, calls.get());
            // The retry interval is large next to this ceiling on purpose: a regression to
            // "keep polling until the key resolves" spends the 5000ms budget here, and even one
            // extra retry overshoots.
            assertTrue(String.format("waited %dms, expected to return on the first response", elapsedMillis),
                    elapsedMillis < 2000);
        } finally {
            server.stop(0);
        }
    }

    @Test(timeOut = 30000)
    public void when_the_service_never_answers_should_stop_at_the_wait_budget() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = this.startServer(exchange -> {
            calls.incrementAndGet();
            // getInputStream throws on a 5xx, so from the client's side nothing answered at all.
            this.respond(exchange, 503, "gone");
        });

        try {
            // No answer means no verdict, so the agent falls back to the local file. What must hold
            // is that it gets there inside the budget rather than sitting on a dead service.
            long started = System.nanoTime();
            assertNull(new VMMetadataClient(this.endpointOf(server))
                    .waitForFirstResponse("teamcity_image_id", 300, 200));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertTrue(String.format("waited %dms, expected to stop near the 300ms budget", elapsedMillis),
                    elapsedMillis < 3000);
            assertTrue(String.format("made %d calls, expected it to retry within the budget", calls.get()),
                    calls.get() >= 2);
        } finally {
            server.stop(0);
        }
    }

    @Test(timeOut = 30000)
    public void when_the_key_is_absent_should_answer_from_the_single_response() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = this.startServer(exchange -> {
            calls.incrementAndGet();
            this.respond(exchange, 200, "{\"error\":\"Unable to find metadata value by provided key x\"}");
        });

        try {
            // What the follow-up lookups rest on: every key is settled before the VM boots, so a
            // key the service answered without is absent rather than still on its way.
            assertNull(new VMMetadataClient(this.endpointOf(server)).getValue("orka_vm_name"));
            assertEquals("expected one request, since no retry can win an absent key", 1, calls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test(timeOut = 30000)
    public void when_a_request_fails_should_repeat_it_and_return_the_value() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = this.startServer(exchange -> {
            // getInputStream throws on a 5xx, so the first attempt reports nothing at all.
            if (calls.incrementAndGet() == 1) {
                this.respond(exchange, 503, "gone");
            } else {
                this.respond(exchange, 200, "{\"value\":\"vm-abc123\"}");
            }
        });

        try {
            assertEquals("vm-abc123", new VMMetadataClient(this.endpointOf(server)).getValue("orka_vm_name", 2));
            assertEquals("expected the failed request to be repeated", 2, calls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test(timeOut = 30000)
    public void when_the_key_is_absent_should_not_spend_the_second_attempt() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = this.startServer(exchange -> {
            calls.incrementAndGet();
            this.respond(exchange, 200, "{\"error\":\"Unable to find metadata value by provided key x\"}");
        });

        try {
            // The retry exists for a request that reported nothing, not for a key the service
            // answered without. Spending it here would charge every deploy without a starting
            // instance id a second round trip for a key that was never sent.
            assertNull(new VMMetadataClient(this.endpointOf(server)).getValue("orka_vm_name", 2));
            assertEquals("expected an answer without the key to end it", 1, calls.get());
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/metadata", handler);
        server.start();
        return server;
    }

    private String endpointOf(HttpServer server) {
        return String.format("http://127.0.0.1:%d/metadata", server.getAddress().getPort());
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream stream = exchange.getResponseBody()) {
            stream.write(bytes);
        }
    }
}
