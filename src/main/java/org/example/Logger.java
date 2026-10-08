package org.example;

import java.util.ArrayList;
import java.util.List;

public class Logger {
    private static List<Event> events = new ArrayList<>();
    public static void addEvent(Event e){
        if(!events.isEmpty()){
            System.out.println("Took : " + secondsSinceLastEventOnThread(e) + " to do " + e.message());
        }
        events.add(e);
    }
    public static List<Event> getEvents(){return events;}
    public static double secondsSinceLastEventOnThread(Event e){
        return (e.time()-events.stream().filter(e2 -> e.thread().equals(e2.thread())).toList().getLast().time())/1000.0;
    }
}