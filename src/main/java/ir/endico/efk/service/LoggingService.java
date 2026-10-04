package ir.endico.efk.service;

import ir.endico.efk.event.LoggingEvent;

public interface LoggingService {

    void log(LoggingEvent event);

}
