package com.topsecret.plugin;

import javax.swing.*;

public interface UIPlugin {
    void startUI();
    void stopUI();
    JPanel createUI();
}
