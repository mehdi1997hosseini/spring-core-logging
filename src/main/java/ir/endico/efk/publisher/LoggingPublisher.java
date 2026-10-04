package ir.endico.efk.publisher;

import ir.endico.efk.event.LoggingEvent;

public interface LoggingPublisher {

    void send(LoggingEvent event);

}
