package com.lamiplus_common_api.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.json.JsonParseException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * A generic publisher to simplify firing events for inter-module communication.
 * Any module can inject this publisher to send events.
 */
@Component
public class GenericEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(GenericEventPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    public GenericEventPublisher(ApplicationEventPublisher applicationEventPublisher, ObjectMapper objectMapper) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes the given payload object to JSON and publishes a generic application event.
     *
     * @param eventType     A string identifier for the event type.
     * @param payloadObject The object to be sent with the event. It will be serialized to JSON.
     */
    public void publish(String eventType, Object payloadObject) throws JsonParseException{
        log.info("Publishing generic JSON event of type '{}'", eventType);
            String jsonPayload = objectMapper.writeValueAsString(payloadObject);
            GenericJsonEvent event = new GenericJsonEvent(this, eventType, jsonPayload);
            applicationEventPublisher.publishEvent(event);
    }
}
