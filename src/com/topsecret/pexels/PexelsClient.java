package com.topsecret.pexels;

import com.secretlib.model.DefaultProgressCallback;
import com.secretlib.model.IProgressCallback;
import com.secretlib.util.HiUtils;
import com.secretlib.util.Log;
import com.topsecret.MainPanel;
import com.topsecret.util.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class PexelsClient {
    private static final Log LOG = new Log(PexelsClient.class);

    public static final String CFG_EXT_PEXELS_API_KEY = "PexelsAPIKey";
    public static final String CFG_EXT_PEXELS_THEME = "PexelsTheme";



    private static PexelsClient instance = null;

    public PexelsClient() {

    }

    public static PexelsClient getInstance() {
        if (instance == null) {
            instance = new PexelsClient();
        }

        return instance;
    }


    private PexelsResult query(String theme, int page) {
        String apiKey = MainPanel.getConfig().getExtendedCfg().getProperty(CFG_EXT_PEXELS_API_KEY);
        if (apiKey == null) {
            LOG.warn("Pexels API key is not defined.");
            return null;
        }

        LOG.debug("Pexels API key : " + apiKey);

        try {
            if ((theme == null) || (theme.length() == 0)) {
                theme = "nature";
            }
            String uri = "https://api.pexels.com/v1/search?query=" + URLEncoder.encode(theme, StandardCharsets.UTF_8.name());
            if (page > 0) {
                uri += "&page=" + page;
            }
            LOG.debug("Calling URL " + uri);

            URL url = new URL(uri);
            HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
            httpConn.setRequestProperty("Accept", "application/json");
            httpConn.setRequestProperty("Content-Type", "application/json");
            httpConn.setRequestProperty("User-Agent", "Pexels/Java");
            httpConn.setRequestProperty("Authorization", apiKey);

            byte[] buf = HiUtils.readAllBytes(httpConn.getInputStream());
            String response = new String(buf, StandardCharsets.UTF_8);
            LOG.debug("response : \n" + response);

            return new PexelsResult(response);

        } catch (MalformedURLException e) {
            LOG.error(e.getMessage());
            return null;
        } catch (IOException e) {
            LOG.error(e.getMessage());
            return null;
        }

    }

    public PexelsPhoto getRandomImage(IProgressCallback cb) {

        String theme = MainPanel.getConfig().getExtendedCfg().getProperty(CFG_EXT_PEXELS_THEME);
        PexelsResult res = query(theme, 0);

        if ((res == null) || (res.getPerPage() == 0)) {
            LOG.error("Nothing returned");
            return null;
        }

        byte[] buf = null;

        int nb = res.getNbResultsTotal();

        int idx = (int) ((double) Math.random() * nb);
        LOG.debug("returning idx " + idx);

        int page = (idx / res.getPerPage()) + 1;
        res = query(theme, page);

        PexelsPhoto photo = res.getPhoto(idx % res.getPerPage());
        String uri = photo.getOriginalUrl();
        LOG.debug("uri : " + uri);

        String apiKey = MainPanel.getConfig().getExtendedCfg().getProperty(CFG_EXT_PEXELS_API_KEY);

        try {
            URL url = new URL(uri);
            HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
            httpConn.setRequestProperty("Accept", "application/json");
            httpConn.setRequestProperty("Content-Type", "application/json");
            httpConn.setRequestProperty("User-Agent", "Pexels/Java");
            httpConn.setRequestProperty("Authorization", apiKey);

            int expectedSize = httpConn.getContentLength();

            buf = Utils.readAllBytesProgress(httpConn.getInputStream(), expectedSize, cb);
            LOG.debug("read bytes : " + buf.length);

            photo.setOriginal(buf);

        } catch (MalformedURLException e) {
            LOG.error(e.getMessage());
            return null;
        } catch (IOException e) {
            LOG.error(e.getMessage());
            return null;
        }

        return photo;
    }


    public static void main(String[] args) {
        Log.setLevel(Log.TRACE);
        PexelsClient c = PexelsClient.getInstance();

        MainPanel.getConfig().getExtendedCfg().setProperty(CFG_EXT_PEXELS_API_KEY, args[0]);
        MainPanel.getConfig().getExtendedCfg().setProperty(CFG_EXT_PEXELS_THEME, "nature");

        PexelsPhoto p = c.getRandomImage(new DefaultProgressCallback());
        byte[] img = p.getOriginal();
        File f = new File("PexelsRandom.jpg");
        FileOutputStream fo = null;
        try {
            fo = new FileOutputStream(f);
            fo.write(img);
            fo.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
