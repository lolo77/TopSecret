package com.topsecret.pexels;

import org.json.*;

public class PexelsResult {

    private JSONArray photos = null;
    private int nbResultsTotal = 0;

    public PexelsResult(String json) {
        JSONObject obj = new JSONObject(json);
        nbResultsTotal = obj.getInt("total_results");
        photos = obj.getJSONArray("photos");
    }

    public int getPerPage() {
        if (photos == null) {
            return 0;
        }

        return photos.length();
    }

    public PexelsPhoto getPhoto(int idx) {
        JSONObject photo = (JSONObject) photos.get(idx);
        if (photo != null) {
            PexelsPhoto p = new PexelsPhoto();
            p.setUrl(photo.getString("url"));
            p.setPhotographer(photo.getString("photographer"));
            p.setPhotographerUrl(photo.getString("photographer_url"));
            p.setDescription(photo.getString("alt"));

            JSONObject src = photo.getJSONObject("src");
            p.setOriginalUrl(src.getString("original"));
            return p;
        }
        return null;
    }
}
