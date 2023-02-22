package com.topsecret.event;

import java.util.ArrayList;
import java.util.List;

public class TopEventDispatcher {
    private List<TopEventListener> listeners = new ArrayList<>();

    private static TopEventDispatcher instance = null;


    public static TopEventDispatcher getInstance() {
        if (instance == null) {
            instance = new TopEventDispatcher();
        }
        return instance;
    }

    public TopEventDispatcher() {

    }

    public void addListener(TopEventListener l) {
        listeners.add(l);
    }

    public void dispatch(TopEventBase e) {
        for (TopEventListener l : listeners) {
            l.processTopEvent(e);
        }
    }
}
