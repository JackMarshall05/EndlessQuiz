package org.example;

public record Event(long time, String message, Thread thread){}