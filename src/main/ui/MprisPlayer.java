package ui;

import model.ExceptionIgnore;
import org.mpris.MediaPlayer2.MediaPlayer2;
import org.mpris.MediaPlayer2.Player;

import java.util.HashMap;
import java.util.Map;

public abstract class MprisPlayer implements Player, MediaPlayer2 {
    @Override
    public void Quit() {
        responderMap.getOrDefault(ResponseTypes.QUIT, in -> System.out.println("No action.")).respond(null);
    }
    
    @Override
    public void Raise() {
        responderMap.getOrDefault(ResponseTypes.RAISE, in -> System.out.println("No action.")).respond(null);
    }
    
    @Override
    public void Next() {
        responderMap.getOrDefault(ResponseTypes.NEXT, in -> System.out.println("No action.")).respond(null);
    }
    
    @Override
    public void OpenUri(String s) {
        responderMap.getOrDefault(ResponseTypes.OPENURI, in -> System.out.println("No action.")).respond(s);
    }
    
    @Override
    public void Pause() {
        responderMap.getOrDefault(ResponseTypes.PAUSE, in -> System.out.println("No action.")).respond(null);
    }
    
    @Override
    public void Play() {
        responderMap.getOrDefault(ResponseTypes.PLAY, in -> System.out.println("No action.")).respond(null);
    }
    
    @Override
    public void PlayPause() {
        responderMap.getOrDefault(ResponseTypes.TOGGLE_PLAYBACK, in -> System.out.println("No action.")).respond(null);
    }
    
    @Override
    public void Previous() {
        responderMap.getOrDefault(ResponseTypes.PREVIOUS, in -> System.out.println("No action.")).respond(null);
    }
    
    @Override
    public void Seek(long l) {
        responderMap.getOrDefault(ResponseTypes.SEEK, in -> System.out.println("No action.")).respond(l);
    }
    
    @Override
    public void Stop() {
        responderMap.getOrDefault(ResponseTypes.STOP, in -> System.out.println("No action.")).respond(null);
    }
    
    @Override
    public String getObjectPath() {
        return "";
    }
    
    private interface Responder {
        void respond(Object in);
    }
    
    public static enum ResponseTypes {
        STOP,
        SEEK,
        PREVIOUS,
        TOGGLE_PLAYBACK,
        PLAY,
        PAUSE,
        OPENURI,
        NEXT,
        RAISE,
        QUIT
    }
    
    private Map<ResponseTypes, Responder> responderMap = new HashMap<>();
    
    public void setResponder(ResponseTypes type, Responder responder) {
        responderMap.put(type, responder);
    }
}
