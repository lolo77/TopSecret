package com.topsecret;

import com.secretlib.util.Log;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.InputStream;

/**
 * @author Florent FRADET
 */
public class Main extends JFrame {


    public Main() {
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Log.setLevel(Log.TRACE);
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("logoflo.png");
            this.setIconImage(ImageIO.read(is));
            is.close();
        } catch (Exception e) {
            // NO OP
        }

        setTitle("Top Secret");

        MainPanel mp = new MainPanel(this);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(mp, BorderLayout.CENTER);
        setSize(350, 560);
        mp.initialize();
        setVisible(true);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                mp.saveConfig();
                e.getWindow().dispose();
            }
        });
    }


    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // NO OP
        }
        Main frame = new Main();
        frame.toFront();
    }
}
