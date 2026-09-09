package ch.so.agi.autotest.util;

import io.restassured.filter.Filter;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import org.testcontainers.containers.output.OutputFrame;

import java.util.List;
import java.util.function.Consumer;

/**
 * Opt-in diagnostics for HTTP API tests. Enable with Gradle's {@code -PhttpTraffic} flag.
 */
public final class HttpTraffic {

    private static final boolean ENABLED = Boolean.getBoolean("httpTraffic");

    private HttpTraffic() {
    }

    public static List<Filter> restAssuredFilters() {
        if (!ENABLED) {
            return List.of();
        }
        return List.of(
            new RequestLoggingFilter(LogDetail.ALL),
            new ResponseLoggingFilter(LogDetail.ALL)
        );
    }

    public static Consumer<OutputFrame> containerLogConsumer(String containerName) {
        return frame -> {
            if (ENABLED && frame.getUtf8String() != null) {
                System.out.print("[%s] %s".formatted(containerName, frame.getUtf8String()));
            }
        };
    }
}
