package com.topsecret.pexels;

public class PexelsPhoto {
    private String url;
    private String photographer;
    private String photographerUrl;
    private String description;

    private String originalUrl;

    private byte[] original;

    public PexelsPhoto() {
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPhotographer() {
        return photographer;
    }

    public void setPhotographer(String photographer) {
        this.photographer = photographer;
    }

    public String getPhotographerUrl() {
        return photographerUrl;
    }

    public void setPhotographerUrl(String photographerUrl) {
        this.photographerUrl = photographerUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public byte[] getOriginal() {
        return original;
    }

    public void setOriginal(byte[] original) {
        this.original = original;
    }
}
