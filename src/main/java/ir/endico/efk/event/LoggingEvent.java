package ir.endico.efk.event;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

@Getter
@Builder
public class LoggingEvent {

    private final String eventId;

    @Builder.Default
    private final Instant timestamp = Instant.now();

    private final LogEventType eventType;

    private final String serviceName;

    private final String environment;

    private final String traceId;

    private final String requestId;

    private final String method;

    private final String address;

    private final String remoteIp;

    private final String userAgent;

    private final String requestBody;

    private final String responseBody;

    private final LogStatus status;

    private final Long duration;

    private final Map<String, Object> metadata;

}