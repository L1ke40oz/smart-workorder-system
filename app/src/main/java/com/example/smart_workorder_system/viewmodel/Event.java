package com.example.smart_workorder_system.viewmodel;

public class Event<T> {
    private final T value;
    private boolean consumed;
    public Event(T value) { this.value=value; }
    public T consume() { if(consumed) return null; consumed=true; return value; }
}
