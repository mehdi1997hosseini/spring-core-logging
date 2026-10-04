package ir.endico.efk.service;

import ir.endico.efk.event.LoggingEvent;
import ir.endico.efk.transport.LoggingTransport;
import org.springframework.stereotype.Service;

@Service
public class DefaultLoggingService implements LoggingService {

    private final LoggingTransport loggingTransport;

    public DefaultLoggingService(LoggingTransport loggingTransport) {
        this.loggingTransport = loggingTransport;
    }

    @Override
    public void log(LoggingEvent event) {
        loggingTransport.send(event);
    }

}