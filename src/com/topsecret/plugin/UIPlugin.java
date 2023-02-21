package com.secretlib.plugin;

import javax.swing.*;

public interface UIPlugin {
    void startUI();
    void stopUI();
    JPanel createUI();
}
