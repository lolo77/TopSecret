package com.topsecret.plugin;

import java.util.Iterator;

public interface ArgsListener {
    boolean consumeArg(Iterator<String> args);
}
