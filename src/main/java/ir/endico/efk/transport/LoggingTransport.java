package ir.endico.efk.transport;

import ir.endico.efk.event.LoggingEvent;

public interface LoggingTransport {

    void send(LoggingEvent event);

}
