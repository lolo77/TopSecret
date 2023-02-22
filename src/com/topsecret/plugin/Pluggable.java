package com.topsecret.plugin;

import com.topsecret.event.TopEventBase;

public interface Pluggable {
    void onPluginEvent(TopEventBase e);
}
