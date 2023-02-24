package com.topsecret.event;


import java.awt.image.BufferedImage;

public class TopEventImageLoaded extends TopEventBase {
    BufferedImage imgLoaded;

    public TopEventImageLoaded(BufferedImage img) {
        imgLoaded = img;
    }

    public BufferedImage getImgLoaded() {
        return imgLoaded;
    }
}
