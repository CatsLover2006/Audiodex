package model;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

// Static class
public class ExceptionIgnore {

    // Effects: do the thing but ignore the exception
    public static void ignoreExc(RunnableExc run) {
        try {
            run.run();
        } catch (Exception e) {
            // The point is to ignore it
            logException(e);
        }
    }

    // Effects: logs exception
    public static void logException(Exception e) {
        AtomicReference<String> str = new AtomicReference<>("");
        Arrays.stream(e.getStackTrace()).forEach(element ->
                str.set(String.format("%s%s\n", str.get(), element.toString())));
        EventLog.getInstance().logEvent(new Event("Exception was ignored:\n" + str.get()));
    }

    // Make a lambda for this
    public interface RunnableExc {
        void run() throws Exception;
    }
}
