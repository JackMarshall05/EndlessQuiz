package org.example;

import java.util.ArrayList;
import java.util.List;

public class Logger {
    private static List<Event> events = new ArrayList<>();
    public static void addEvent(String str){events.add(new Event(str));}
    public static List<Event> getEvents(){return events;}
}