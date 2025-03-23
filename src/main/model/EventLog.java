package model;

import org.fusesource.jansi.AnsiConsole;

import java.util.LinkedList;
import java.util.Collection;
import java.util.Iterator;

/**
 * Represents a log of alarm system events.
 * We use the Singleton Design Pattern to ensure that there is only
 * one EventLog in the system and that the system has global access
 * to the single instance of the EventLog.
 */
public class EventLog implements Iterable<Event> {
    /** the only EventLog in the system (Singleton Design Pattern) */
    private static EventLog theLog;
    private LinkedList<Event> events;
    
    /**
     * Prevent external construction.
     * (Singleton Design Pattern).
     */
    private EventLog() {
        events = new LinkedList<Event>();
    }
    
    /**
     * Gets instance of EventLog - creates it
     * if it doesn't already exist.
     * (Singleton Design Pattern)
     * @return  instance of EventLog
     */
    public static EventLog getInstance() {
        if (theLog == null) {
            theLog = new EventLog();
        }
        return theLog;
    }
    
    /**
     * Adds an event to the event log.
     * @param e the event to be added
     */
    public void logEvent(Event e) {
        events.add(e);
        // Keep size under control
        if (events.size() > 4096) {
            // When we accumulate 4096 events, dump all but 1024 to the console
            while (events.size() > 1024) {
                Event event = events.removeLast();
                AnsiConsole.out().println(String.format("%s: %s",
                        event.getDate().toString(), event.getDescription()));
            }
        }
    }
    
    /**
     * Clears the event log and logs the event.
     */
    public void clear() {
        events.clear();
        logEvent(new Event("Event log cleared."));
    }
    
    @Override
    public Iterator<Event> iterator() {
        return events.iterator();
    }
}