package org.example;

import java.time.LocalTime;

public class Event{
    public final LocalTime time = LocalTime.now();
    public final Thread thread = Thread.currentThread();
    public final String message;
    public Event(String message){
        this.message = message;
        System.out.println(time + " logged " + message + " on Thread:" + thread);
    }
}