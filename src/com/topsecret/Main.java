package com.topsecret;

import com.secretlib.util.Log;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;

/**
 * @author Florent FRADET
 */
public class Main extends JFrame {

    private static final Log LOG = new Log(Main.class);

    private String inputFile = null;
    private String passM = null;
    private String passD = null;

    private String hash = null;

    private boolean showDlg = true;

    public Main(String[] args) {
        parseArgs(args);

        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("favicon64.png");
            this.setIconImage(ImageIO.read(is));
            is.close();
        } catch (Exception e) {
            // NO OP
        }

        setTitle("Top Secret");

        MainPanel mp = new MainPanel(this);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(mp, BorderLayout.CENTER);
        setSize(450, 560);
        mp.loadConfig();
        if (hash != null) {
            mp.getCfg().setAlgo(hash);
        }
        mp.initialize();
        if (passM != null) {
            mp.setPassMaster(passM);
        }
        if (passD != null) {
            mp.setPassData(passD);
        }
        if (inputFile != null) {
            mp.setInputImage(new File(inputFile));
        }
        mp.setShowDlg(showDlg);

        setVisible(true);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                mp.saveConfig();
                e.getWindow().dispose();
            }
        });
    }


    private void parseArgs(String[] args) {
        Iterator<String> iter = Arrays.stream(args).iterator();
        while (iter.hasNext()) {
            String arg = iter.next();
            if (arg.startsWith("-")) {
                if ("-quiet".equals(arg)) {
                    showDlg = false;
                } else if ("-tron".equals(arg)) {
                    Log.setLevel(Log.TRACE);
                } else if ("-h".equals(arg)) {
                    if (iter.hasNext()) {
                        hash = iter.next();
                    }
                } else if ("-pm".equals(arg)) {
                    if (iter.hasNext()) {
                        passM = iter.next();
                    }
                } else if ("-pd".equals(arg)) {
                    if (iter.hasNext()) {
                        passD = iter.next();
                    }
                } else {
                    LOG.error("invalid arg : " + arg);
                }
            } else {
                inputFile = arg;
            }
        }

    }

    public static void main(String[] args) {
        Log.setLevel(Log.CRITICAL);


        Main frame = new Main(args);
        frame.toFront();
    }
}
