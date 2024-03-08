package model;

// Static class
public class ExceptionIgnore {

    // Effects: do the thing but ignore the exception
    public static void ignoreExc(RunnableExc run) {
        try {
            run.run();
        } catch (Exception e) {
            // The point is to ignore it
            e.printStackTrace();
        }
    }

    // Make a lambda for this
    public interface RunnableExc {
        void run() throws Exception;
    }
}
