package com.topsecret.plugin;

import com.secretlib.util.Log;
import com.topsecret.event.TopEventBase;
import com.topsecret.event.TopEventDispatcher;
import com.topsecret.event.TopEventPluginStart;
import com.topsecret.event.TopEventPluginStop;
import com.topsecret.util.Utils;

import java.io.File;
import java.io.FilenameFilter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PluginManager {

    private static final Log LOG = new Log(PluginManager.class);

    public static PluginManager instance = null;

    private List<Pluggable> plugins = new ArrayList<>();


    public static PluginManager getInstance() {
        if (instance == null) {
            instance = new PluginManager();
        }
        return instance;
    }


    public PluginManager() {

    }


    public void discoverPlugins() {
        File f = new File(".");
        File[] jars = f.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".jar");
            }
        });

        for (File jar : jars) {
            loadPlugin(jar.getName());
        }
    }


    public void loadPlugin(String jarName) {
        try {
            LOG.info("Loading plugin " + jarName);

            File f = new File(jarName);
            URLClassLoader child = new URLClassLoader(
                    new URL[]{f.toURI().toURL()},
                    this.getClass().getClassLoader()
            );
            String simpleName = f.getName();
            String pluginName = Utils.removeExt(simpleName);
            String fullClassName = "com.topsecret.plugin." + pluginName.toLowerCase() + ".Main";
            LOG.debug("Full class name : " + fullClassName);
            Class classToLoad = Class.forName(fullClassName, true, child);

            Object instance = classToLoad.getDeclaredConstructor().newInstance();
            if (instance instanceof Pluggable) {
                plugins.add((Pluggable) instance);
            } else {
                LOG.warn("Attempted to load " + jarName + " as a plugin but it does not extend the Pluggable interface.");
            }
        } catch (Exception e) {
            LOG.error(e.getMessage());
            e.printStackTrace();
        }
    }



    public void startAll() {
        dispatchPluginEvent(new TopEventPluginStart());
    }


    public void stopAll() {
        dispatchPluginEvent(new TopEventPluginStop());
    }

    public boolean consumeArg(Iterator<String> args) {
        Iterator<Pluggable> iter = plugins.iterator();
        while (iter.hasNext()) {
            Pluggable p = iter.next();
            if (p instanceof ArgsListener) {
                ArgsListener al = (ArgsListener) p;
                if (al.consumeArg(args))
                    return true;
            }
        }
        return false;
    }

    public void dispatchPluginEvent(TopEventBase e) {
        Iterator<Pluggable> iter = plugins.iterator();
        while (iter.hasNext()) {
            Pluggable p = iter.next();
            p.onPluginEvent(e);
        }
    }

    public void dispatchHostEvent(TopEventBase e) {
        TopEventDispatcher.getInstance().dispatch(e);
    }
}
