package com.topsecret.util;


import java.awt.*;
import java.io.File;

public class Config {

    private String algo;
    private int bitStart = 0;
    private boolean bitExt = true;
    private String lastOpenDir;
    private Rectangle frameRect;
    private String lang;

    public String getAlgo() {
        return algo;
    }

    public void setAlgo(String algo) {
        this.algo = algo;
    }

    public int getBitStart() {
        return bitStart;
    }

    public void setBitStart(int bitStart) {
        this.bitStart = bitStart;
    }

    public boolean isBitExt() {
        return bitExt;
    }

    public void setBitExt(boolean bitExt) {
        this.bitExt = bitExt;
    }

    public String getLastOpenDir() {
        return lastOpenDir;
    }

    public void setLastOpenDir(String lastOpenDir) {
        this.lastOpenDir = lastOpenDir;
    }

    public Rectangle getFrameRect() {
        return frameRect;
    }

    public void setFrameRect(Rectangle frameRect) {
        this.frameRect = frameRect;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public static String getPath(String filename) {
        int idx = filename.lastIndexOf("/");
        if (idx < 0) {
            idx = filename.lastIndexOf("\\");
        }
        if (idx > 0) {
            return filename.substring(0, idx);
        }
        return "";
    }

    public void updateLastOpenDir(File file) {
        setLastOpenDir(getPath(file.getAbsolutePath()));
    }
}
