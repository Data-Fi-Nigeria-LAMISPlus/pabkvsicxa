package com.lamiplus_common_api.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * A generic application event that carries its payload as a JSON string.
 * This avoids direct class dependencies between modules while allowing complex
 * objects to be passed as event data.
 */
@Getter
public class GenericJsonEvent extends ApplicationEvent {

    private final String eventType;
    private final String jsonPayload;

    /**
     * Create a new GenericJsonEvent.
     *
     * @param source      The component that is publishing the event (never {@code null}).
     * @param eventType   A non-null, non-empty string identifying the event's type.
     * @param jsonPayload A string containing the event data in JSON format.
     */
    public GenericJsonEvent(Object source, String eventType, String jsonPayload) {
        super(source);
        if (eventType.trim().isEmpty()) {
            throw new IllegalArgumentException("eventType cannot be null or empty");
        }
        this.eventType = eventType;
        this.jsonPayload = jsonPayload;
    }

}
