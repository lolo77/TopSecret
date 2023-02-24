package com.topsecret.event;


import java.io.File;

public class TopEventEvaluateSpace extends TopEventBase {

    public static class SpaceInfo {
        int[] space = null;

        public SpaceInfo() {
        }

        public int[] getSpace() {
            return space;
        }

        public void setSpace(int[] space) {
            this.space = space;
        }

    }

    File inputFile;
    SpaceInfo space;
    Double visualAlterationFactor;
    String codec;

    public TopEventEvaluateSpace(File inputFile, String codec) {
        this.inputFile = inputFile;
        space = null;
        this.codec = codec;
        visualAlterationFactor = null;
    }

    public File getInputFile() {
        return inputFile;
    }

    public SpaceInfo getSpace() {
        return space;
    }

    public String getCodec() {
        return codec;
    }

    public void setVisualAlterationFactor(Double visualAlterationFactor) {
        this.visualAlterationFactor = visualAlterationFactor;
    }

    public Double getVisualAlterationFactor() {
        return visualAlterationFactor;
    }
}
