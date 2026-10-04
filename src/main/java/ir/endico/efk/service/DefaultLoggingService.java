package ir.endico.efk.service;

import ir.endico.efk.event.LoggingEvent;
import ir.endico.efk.publisher.LoggingPublisher;
import org.springframework.stereotype.Service;

@Service
public class DefaultLoggingService implements LoggingService {

    private final LoggingPublisher loggingPublisher;

    public DefaultLoggingService(LoggingPublisher loggingPublisher) {
        this.loggingPublisher = loggingPublisher;
    }

    @Override
    public void log(LoggingEvent event) {
        loggingPublisher.send(event);
    }

}