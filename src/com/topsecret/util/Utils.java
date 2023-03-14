package com.topsecret.util;

import com.secretlib.model.IProgressCallback;
import com.secretlib.model.ProgressMessage;
import com.secretlib.model.ProgressStepEnum;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

/**
 * @author Florent FRADET
 */
public class Utils {

    public static boolean isAlgoSupported(String algo) {
        try {
            MessageDigest md = MessageDigest.getInstance(algo);
        } catch (NoSuchAlgorithmException var4) {
            return false;
        }
        return true;
    }


    public static void addNumericValidator(JTextField source, long minVal, long maxVal) {
        source.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (!evt.getPropertyName().equals(CoolJTextField.TEXT_PROPERTY)) {
                    return;
                }
                String newValue = (String)evt.getNewValue();
                if ((newValue.equals("-")) || (newValue.length() == 0)) {
                    return;
                }
                try {
                    long newVal = Long.parseLong(newValue);
                    if ((newVal < minVal) || (newVal > maxVal)) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException ex) {
                    source.setText((String)evt.getOldValue());
                }
            }

        });
    }

    public static String getFileExt(File f) {
        String s = null;
        if (f != null && !f.isDirectory()) {
            String sName = f.getName();
            int i = sName.lastIndexOf(".");
            if (i >= 0) {
                s = sName.substring(i + 1);
                s = s.toLowerCase();
            }
        }
        return s;
    }

    public static String removeExt(String name) {
        int idxLastDot = name.lastIndexOf(".");
        if (idxLastDot < 0) {
            return "";
        }
        return name.substring(0, idxLastDot);
    }

    public static String[] asArray(Set<String> set) {
        String[] tab = new String[set.size()];
        int i = 0;
        for (String s : set) {
            tab[i++] = s;
        }
        return tab;
    }

    public static byte[] readAllBytesProgress(InputStream in, int expectedSize, IProgressCallback cb) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];

        ProgressMessage pm = new ProgressMessage(ProgressStepEnum.DOWNLOAD, 0);
        int iRead;
        int totalRead = 0;
        int lastUpdate = 0;
        while((iRead = in.read(tmp)) > 0) {
            buf.write(tmp, 0, iRead);
            totalRead += iRead;
            if ((cb != null) && (totalRead - lastUpdate >= 128 * 1024)) {
                // Update every 128kiB
                lastUpdate = totalRead;
                pm.setProgress((double)((double)totalRead / (double)expectedSize));
                cb.update(pm);
            }
        }

        return buf.toByteArray();
    }
}
