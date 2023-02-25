package com.topsecret;

import com.secretlib.exception.NoBagException;
import com.secretlib.exception.TruncatedBagException;
import com.secretlib.io.stream.HiDataAbstractInputStream;
import com.secretlib.io.stream.HiDataAbstractOutputStream;
import com.secretlib.io.stream.HiDataPdfOutputStream;
import com.secretlib.io.stream.HiDataStreamFactory;
import com.secretlib.model.*;
import com.secretlib.util.HiUtils;
import com.secretlib.util.Log;
import com.secretlib.util.Parameters;
import com.topsecret.event.*;
import com.topsecret.model.DataItem;
import com.topsecret.model.DataModel;
import com.topsecret.plugin.PluginManager;
import com.topsecret.server.DataServer;
import com.topsecret.util.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.List;
import java.util.*;


/**
 * @author Florent FRADET
 * <p>
 * Main Swing GUI class
 */
public class MainPanel extends JPanel implements TopEventListener {

    private static final Log LOG = new Log(MainPanel.class);
    private final static int IMAGE_SIZE = 100;

    ResourceBundle bundle;
    JFrame frame;

    JButton btnSelectImage;
    JButton btnRefreshImage;
    JButton btnRefreshData;
    JButton btnEncode;
    JButton btnEncodeTo;
    JButton btnDecodeTo;
    JButton btnDelete;
    JButton btnNew;

    JPasswordField passMaster;
    JPasswordField passData;
    JComboBox<String> algo;
    JTextField bitStart;
    JCheckBox bitExtend;

    JLabel img;
    JLabel lblSpaceTotal;
    JLabel lblSpaceUsed;
    JLabel lblSpaceFree;
    JLabel lblAltFact;
    JProgressBar progress;
    JLabel progressStep;

    JComboBox<String> cmbDecode;
    JComboBox<String> cmbEncode;

    JScrollPane scrollPane;
    JTable tableData;
    private final DataModel data = new DataModel(this);

    private Config cfg = new Config();
    private int defaultAlgoIdx;
    private HiDataBag bag = new HiDataBag();
    private int iBitStart;
    private File inputFile = null;

    private boolean showDlg = true;

    private int[] spaceCapacity = new int[8];


    private DataServer dataServer = null;


    public MainPanel(JFrame frame) {
        this.frame = frame;
        Arrays.fill(spaceCapacity, 0);

        try {
            dataServer = new DataServer();
        } catch (Exception e) {
            LOG.warn("Exception while creating dataServer : " + e.getMessage());
            dataServer = null;
        }
    }


    public String getString(String key, Object... args) {
        String s = null;
        try {
            s = bundle.getString(key);
        } catch (Exception e) {
            s = "[key : " + key + "]";
        }
        return MessageFormat.format(s, args);
    }


    public int getSpaceCapacity() {
        int iBitEnd = bitExtend.isSelected() ? 7 : iBitStart;
        int cap = 0;
        for (int i = iBitStart; i <= iBitEnd; i++) {
            if (spaceCapacity[i] == Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            cap += spaceCapacity[i];
        }
        return cap;
    }


    public void updateAlterationFactor() {
        long used = (bag.isEmpty() ? 0 : bag.getLength()) * 8L;
        if (iBitStart < 0) {
            iBitStart = 0;
        }
        if (iBitStart > 7) {
            iBitStart = 7;
        }
        int iBitEnd = bitExtend.isSelected() ? 7 : iBitStart;
        long cap = 0;
        int i = iBitStart;
        double af = 0.0;
        for (; i <= iBitEnd; i++) {
            cap += spaceCapacity[i];
            int part = 1 << (8 - i);
            if (used < cap) {
                double ratioOnBit = 1.0 - ((double) (cap - used) / (double) spaceCapacity[i]);
                af += (1.0 / (double) part) * ratioOnBit;
                break;
            } else if (cap > 0) {
                af += 1.0 / (double) part;
            }
        }
        af *= 100.0;
        lblAltFact.setText(getString("output.visualAlt", String.format("%.02f", af)));
        if (af < 10) {
            lblAltFact.setBackground(new Color(0, 150, 0));
            lblAltFact.setForeground(Color.WHITE);
        } else if (af < 25) {
            lblAltFact.setBackground(Color.ORANGE);
            lblAltFact.setForeground(Color.BLACK);
        } else {
            lblAltFact.setBackground(Color.RED);
            lblAltFact.setForeground(Color.WHITE);
        }

    }


    public boolean validateInputs() {
//        StringBuilder sb = new StringBuilder();

        String sBitStart = bitStart.getText();
        try {
            iBitStart = Integer.parseInt(sBitStart);
        } catch (NumberFormatException e) {
            iBitStart = 0;
        }

        String sAlgo = (String) algo.getSelectedItem();
        if (!Utils.isAlgoSupported(sAlgo)) {
            algo.setSelectedIndex(defaultAlgoIdx);
        }

        return true;
    }

    public void setHashAlgo(String s) {

    }

    public void setPassMaster(String s) {
        passMaster.setText(s);
    }

    public void setPassData(String s) {
        passData.setText(s);
    }

    public void setShowDlg(boolean b) {
        showDlg = b;
    }

    private Parameters buildParams(boolean encode) {
        Parameters p = new Parameters();
        ProgressCB progCB = new ProgressCB();
        p.setProgressCallBack(progCB);
        p.setKm(String.copyValueOf(passMaster.getPassword()));
        p.setKd(String.copyValueOf(passData.getPassword()));
        p.setHashAlgo((String) algo.getSelectedItem());
        p.setBitStart(iBitStart);
        p.setAutoExtendBit(bitExtend.isSelected());
        p.setCodec((String) (encode ? cmbEncode.getSelectedItem() : cmbDecode.getSelectedItem()));

        return p;
    }

    private boolean hasUnencryptedData() {
        for (AbstractChunk c : bag.getItems())
            if (c instanceof ChunkData) {
                ChunkData d = (ChunkData) c;
                if (!d.isEncrypted())
                    return true;
            }
        return false;
    }

    private void updateSpace() {
        long used = (bag.isEmpty() ? 0 : bag.getLength()); // To avoid displaying the minimal required bytes
        long total = (getSpaceCapacity() == Integer.MAX_VALUE) ? Integer.MAX_VALUE : getSpaceCapacity() / 8; // bytes
        lblSpaceTotal.setText(getString("output.space.total", total));
        long free = (total - used);
        String s = "";
        if (free >= 0) {
            if (total > 0) {
                s = getString("output.space.freePerc", free, (free * 100 / total));
            } else {
                s = getString("output.space.free", free);
            }
            lblSpaceFree.setText(s);
            lblSpaceFree.setForeground(Color.BLACK);
        } else {
            lblSpaceFree.setText(getString("output.space.require", -free));
            lblSpaceFree.setForeground(Color.RED);
        }

        if (total > 0) {
            s = getString("output.space.usedPerc", used, (used * 100 / total));
        } else {
            s = getString("output.space.used", used);
        }
        lblSpaceUsed.setText(s);

        boolean b = ((inputFile != null) && (free >= 0));
        btnEncode.setEnabled(b);
        btnEncodeTo.setEnabled(b);
        btnDecodeTo.setEnabled(b && (hasUnencryptedData()));

        updateAlterationFactor();
    }


    public void refreshView() {
        data.getLstItems().clear();

        for (int i = 0; i < bag.getItems().size(); i++) {
            AbstractChunk df = bag.getItems().get(i);

            if (EChunk.DATA.equals(df.getType())) {
                ChunkData dfd = (ChunkData) df;
                DataItem line = new DataItem(bag, dfd.getName(), dfd.getTotalLength(), dfd.isEncrypted(), dfd.getId());

                data.getLstItems().add(line);
            }
        }
        scrollPane.invalidate();
        tableData.getSelectionModel().clearSelection();
        updateSpace();
        scrollPane.repaint();
    }

    private void onEvaluateSpace(TopEventEvaluateSpace e) {
        if (e.getInputFile() == null) {
            // No op
            return;
        }
        Parameters p = buildParams(true);
        p.setCodec(e.getCodec());
        ProgressCBLastMsg cb = new ProgressCBLastMsg();
        p.setProgressCallBack(cb);
        try {
            OutputStream out = new NullOutputStream();
            HiDataAbstractOutputStream os = HiDataStreamFactory.createOutputStream(new FileInputStream(e.getInputFile()), out, p);
            os.write(0);
            os.close();
            spaceCapacity = cb.getLastMsgCapacity().getNbBitsTotals();
            updateSpace();
        } catch (Exception ex) {
            LOG.debug("onEvaluateSpace : " + ex.getMessage());
        }
    }

    @Override
    public void processTopEvent(TopEventBase e) {
        if (e instanceof TopEventEvaluateSpace) {
            PluginManager.getInstance().dispatchPluginEvent(e);
            onEvaluateSpace((TopEventEvaluateSpace) e);
        }
        if (e instanceof TopEventInputFileChanged) {
            TopEventInputFileChanged e2 = (TopEventInputFileChanged)e;
            PluginManager.getInstance().dispatchPluginEvent(e);
            TopEventDispatcher.getInstance().dispatch(new TopEventEvaluateSpace(e2.getFile(), (String) cmbEncode.getSelectedItem()));
        }
        if (e instanceof TopEventImageLoaded) {
            PluginManager.getInstance().dispatchPluginEvent(e);
        }
        if (e instanceof TopEventAttachmentChanged) {
            PluginManager.getInstance().dispatchPluginEvent(e);
        }
    }

    private void selectOutputCodec(String codec) {
        cmbEncode.getModel().setSelectedItem(codec);
    }

    private class ProgressCBLastMsg implements IProgressCallback {

        private ProgressMessage lastMsg = null;
        private ProgressMessage lastMsgCapacity = null;


        public ProgressCBLastMsg() {
        }

        @Override
        public void update(ProgressMessage progressMessage) {
            lastMsg = progressMessage;
            if ((progressMessage.getNbBitsTotals() != null) && (progressMessage.getNbBitsTotal(0) > 0)) {
                lastMsgCapacity = progressMessage;
            }
        }

        public ProgressMessage getLastMsg() {
            return lastMsg;
        }

        public ProgressMessage getLastMsgCapacity() {
            return lastMsgCapacity;
        }
    }

    private class ProgressCB extends ProgressCBLastMsg {

        private final HashMap<ProgressStepEnum, String> msgs = new HashMap<>();

        public ProgressCB() {
            msgs.put(ProgressStepEnum.DECODE, getString("process.decode"));
            msgs.put(ProgressStepEnum.ENCODE, getString("process.encode"));
            msgs.put(ProgressStepEnum.READ, getString("process.read"));
            msgs.put(ProgressStepEnum.WRITE, getString("process.write"));
        }

        @Override
        public void update(ProgressMessage progressMessage) {
            super.update(progressMessage);

            SwingUtilities.invokeLater(() -> {
                if (!progress.isVisible()) {
                    progress.setVisible(true);
                }
                progress.setValue((int) (progressMessage.getProgress() * 100.0));
                progressStep.setText(msgs.get(progressMessage.getStep()));
            });
        }

    }


    private void loadSource(File file) throws TruncatedBagException {
        Parameters p = buildParams(false);

        SwingUtilities.invokeLater(() -> {
            progress.setMinimum(0);
            progress.setMaximum(100);
            progress.setValue(0);
            progress.setVisible(true);
            progressStep.setVisible(true);
        });
        FileInputStream fis = null;
        boolean truncated = false;
        try {
            fis = new FileInputStream(file);
            HiDataAbstractInputStream hdis = HiDataStreamFactory.createInputStream(fis, p);
            if (hdis == null)
                throw new Exception(getString("input.err.file.format", file.getName()));
            selectOutputCodec(hdis.getOutputCodecName());
            HiDataBag newBag = hdis.getBag();
            if (!newBag.isEmpty()) {
                // Replace the displayed bag by the one found in the source image
                bag = newBag;
                bag.decryptAll(p);

                if (showDlg) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(MainPanel.this,
                                getString("input.info.found"),
                                getString("input.title"),
                                JOptionPane.INFORMATION_MESSAGE);
                    });
                }
            }
        } catch (TruncatedBagException e) {
            truncated = true;
        } catch (NoBagException e) {
            // No bag
            if (showDlg) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(MainPanel.this,
                            getString("input.info.notFound"),
                            getString("input.title"),
                            JOptionPane.INFORMATION_MESSAGE);
                });
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(MainPanel.this,
                    getString("input.err.file.data", e.getMessage()),
                    getString("input.title"),
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    // NO OP
                }
            }
        }
        SwingUtilities.invokeLater(() -> {
            ProgressCB progCB = (ProgressCB) p.getProgressCallBack();
            if (progCB.getLastMsgCapacity() != null) {
                spaceCapacity = progCB.getLastMsgCapacity().getNbBitsTotals();
            }
            refreshView();
            progress.setVisible(false);
            progressStep.setText(file.getName());
        });
        if (truncated)
            throw new TruncatedBagException();
    }


    public void onOpenSource() {
        if (!validateInputs()) {
            return;
        }
        JFileChooser fileChooser = new JFileChooser();
        if (cfg.getLastOpenDir() != null) {
            File fDir = new File(cfg.getLastOpenDir());
            if (fDir.isDirectory()) {
                fileChooser.setCurrentDirectory(fDir);
            }
        }
        // Set extension filter
        fileChooser.setAcceptAllFileFilterUsed(true);
        List<String> lstExts = HiDataStreamFactory.getSupportedInputExtensions();
        String sExts = String.join(", ", lstExts);
        fileChooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    return true;
                }
                String ext = Utils.getFileExt(f);
                if (ext == null)
                    return false;
                ext = ext.toLowerCase(Locale.ROOT);
                return lstExts.contains(ext);
            }

            @Override
            public String getDescription() {
                return getString("input.filter.ext", sExts);
            }
        });

        // Show save file dialog
        int res = fileChooser.showOpenDialog(this);

        if (res == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            onRefreshImage(file);
        }
    }


    private void replaceImg(BufferedImage bi) {
        int w = bi.getWidth();
        int h = bi.getHeight();
        int newSize = IMAGE_SIZE;
        if (w >= h) {
            h = h * newSize / w;
            w = newSize;
        } else {
            w = w * newSize / h;
            h = newSize;
        }
        ImageIcon icon = new ImageIcon(bi.getScaledInstance(w, h, Image.SCALE_SMOOTH));
        img.setIcon(icon);
    }


    public void onRefreshImage(File file) {
        if ((file == null) || (!file.exists()) || (file.isDirectory()))
            return;

        String sExt = Utils.getFileExt(file);
        List<String> lstExts = HiDataStreamFactory.getSupportedInputExtensions();
        String sExts = String.join(", ", lstExts);
        if (!lstExts.contains(sExt)) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(MainPanel.this,
                        getString("input.err.ext", sExts),
                        getString("input.title"),
                        JOptionPane.ERROR_MESSAGE);
            });
            return;
        }

        btnRefreshImage.setEnabled(false);
        btnRefreshData.setEnabled(false);
        btnSelectImage.setEnabled(false);
        btnEncode.setEnabled(false);
        btnEncodeTo.setEnabled(false);
        btnDecodeTo.setEnabled(false);
        btnDelete.setEnabled(false);

        new Thread() {
            public void run() {
                try {
                    SwingUtilities.invokeLater(() -> {
                        try {
                            File imgFile = file;
                            HiDataPdfOutputStream pdfIS = new HiDataPdfOutputStream();
                            if (pdfIS.matches(sExt)) {
                                imgFile = new File("./res/pdf.png");
                            }
                            BufferedImage bi = ImageIO.read(imgFile);
                            replaceImg(bi);
                        } catch (IOException e) {
                            // NO OP
                        }
                    });
                    loadSource(file);
                    cfg.updateLastOpenDir(file);
                    inputFile = file;
                    TopEventDispatcher.getInstance().dispatch(new TopEventInputFileChanged(inputFile));
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        String msg = (e.getMessage() != null) ? getString("input.err.file.data", e.getMessage()) : getString("input.err.file.data");
                        if (e instanceof TruncatedBagException) {
                            msg = getString("input.err.trunc");
                        }
                        JOptionPane.showMessageDialog(MainPanel.this,
                                msg,
                                getString("input.title"),
                                JOptionPane.ERROR_MESSAGE);
                    });
                }
                SwingUtilities.invokeLater(() -> {
                    btnRefreshImage.setEnabled(true);
                    btnRefreshData.setEnabled(true);
                    btnSelectImage.setEnabled(true);
                });
            }
        }.start();
    }

    public void onRefreshData() {
        Parameters p = buildParams(false);
        try {
            bag.decryptAll(p);
        } catch (Exception e) {
            // NO OP
        }
        SwingUtilities.invokeLater(() -> {
            refreshView();
        });
    }


    public void onDecode() {
        if (!validateInputs()) {
            return;
        }
        JFileChooser dirChooser = new JFileChooser();
        if (cfg.getLastOpenDir() != null) {
            File fDir = new File(cfg.getLastOpenDir());
            dirChooser.setCurrentDirectory(fDir);
        }
        dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        // Show save file dialog
        int res = dirChooser.showOpenDialog(this);

        if (res == JFileChooser.APPROVE_OPTION) {
            File file = dirChooser.getSelectedFile();
            Parameters p = buildParams(false);

            btnRefreshImage.setEnabled(false);
            btnSelectImage.setEnabled(false);
            btnRefreshData.setEnabled(false);
            btnEncode.setEnabled(false);
            btnEncodeTo.setEnabled(false);
            btnDecodeTo.setEnabled(false);
            btnDelete.setEnabled(false);

            SwingUtilities.invokeLater(() -> {
                progress.setMinimum(0);
                progress.setMaximum(100);
                progress.setValue(0);
                progressStep.setVisible(true);
            });

            new Thread() {
                public void run() {
                    try {
                        int chunkNum = 0;
                        int nbData = 0;
                        if (bag == null) {
                            return;
                        }
                        for (AbstractChunk d : bag.getItems()) {

                            if (EChunk.DATA.equals(d.getType())) {
                                ChunkData dfd = (ChunkData) d;
                                if (!dfd.isEncrypted()) {
                                    nbData++;
                                    String sFileName = dfd.getName();
                                    if (sFileName == null) {
                                        sFileName = getString("decoder.dataChunk.name", chunkNum);
                                        chunkNum++;
                                    }
                                    File f = new File(file.getAbsolutePath() + File.separator + sFileName);
                                    FileOutputStream fos = new FileOutputStream(f);
                                    ((ChunkData) d).write(fos);
                                    fos.close();
                                }
                            }
                        }
                        if (showDlg) {
                            final int nb = nbData;
                            SwingUtilities.invokeLater(() -> {
                                JOptionPane.showMessageDialog(MainPanel.this,
                                        getString("decoder.extract", nb, file.getAbsolutePath()),
                                        getString("decoder.success"),
                                        JOptionPane.INFORMATION_MESSAGE);
                            });
                        }
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(MainPanel.this,
                                    getString("decoder.error.tryAgain"),
                                    getString("decoder.error"),
                                    JOptionPane.ERROR_MESSAGE);
                        });
                    }
                    SwingUtilities.invokeLater(() -> {
                        progress.setVisible(false);
                        progressStep.setText(inputFile.getName());

                        btnRefreshImage.setEnabled(true);
                        btnSelectImage.setEnabled(true);
                        btnRefreshData.setEnabled(true);
                        updateSpace();
                    });
                }
            }.start();
        }
    }


    public void onExport() {
        if (!validateInputs()) {
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        if (cfg.getLastOpenDir() != null) {
            File fDir = new File(cfg.getLastOpenDir());
            fileChooser.setCurrentDirectory(fDir);
        }
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        // Show save file dialog
        int res = fileChooser.showSaveDialog(this);

        if (res == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            Parameters p = buildParams(true);
            try {
                FileOutputStream fout = new FileOutputStream(file);
                bag.encryptAll(p);
                fout.write(bag.toByteArray());
                fout.close();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(MainPanel.this,
                            getString("export.error.msg", e.getMessage()),
                            getString("export.error"),
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        }
    }

    public void onEncode(boolean to) {

        if (!validateInputs()) {
            return;
        }

        File file = inputFile;

        if (to) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setCurrentDirectory(inputFile);


            // Set extension filter
            fileChooser.setAcceptAllFileFilterUsed(true);
            String codec = (String) cmbEncode.getSelectedItem();
            List<String> lstExts = HiDataStreamFactory.getAbstractOutputStreamForCodec(codec).getExtensions();
            String sExts = String.join(", ", lstExts);
            fileChooser.setFileFilter(new FileFilter() {
                @Override
                public boolean accept(File f) {
                    if (f.isDirectory()) {
                        return true;
                    }
                    String ext = Utils.getFileExt(f);
                    if (ext == null)
                        return false;
                    ext = ext.toLowerCase(Locale.ROOT);
                    return lstExts.contains(ext);
                }

                @Override
                public String getDescription() {
                    return getString("input.filter.ext", sExts);
                }
            });


            // Show save file dialog
            int res = fileChooser.showSaveDialog(this);
            if (res == JFileChooser.APPROVE_OPTION) {
                file = fileChooser.getSelectedFile();
            } else {
                return;
            }
        }

        if (file != null) {
            final File fFile = file;
            SwingUtilities.invokeLater(() -> {
                btnRefreshImage.setEnabled(false);
                btnSelectImage.setEnabled(false);
                btnRefreshData.setEnabled(false);
                btnEncode.setEnabled(false);
                btnEncodeTo.setEnabled(false);
                btnDecodeTo.setEnabled(false);
                btnDelete.setEnabled(false);

                progress.setMinimum(0);
                progress.setMaximum(100);
                progress.setValue(0);
                progressStep.setVisible(true);
            });

            Thread runner = new Thread() {
                public void run() {
                    File fTemp = null;
                    try {
                        Parameters p = buildParams(true);
                        File outputFile = new File(fFile.getAbsolutePath());
                        if (fFile.equals(inputFile)) {
                            fTemp = new File(fFile.getAbsolutePath() + ".tmp");
                            fTemp.delete();
                            if (!fFile.renameTo(fTemp)) {
                                throw new RuntimeException("Could not create " + fTemp.getAbsolutePath());
                            }
                        } else {
                            fTemp = inputFile;
                        }
                        // in and out must not be the same file or the input file would be squizzed (length set to 0).
                        FileInputStream fis = new FileInputStream(fTemp);
                        FileOutputStream fos = new FileOutputStream(outputFile);
                        HiDataAbstractOutputStream out = HiDataStreamFactory.createOutputStream(fis, fos, p);
                        if (out == null)
                            throw new Exception(getString("encoder.error.codec", p.getCodec()));
                        bag.encryptAll(p);
                        out.write(bag.toByteArray());
                        out.close();
                    } catch (Exception e) {
                        LOG.error(e.getMessage());
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(MainPanel.this,
                                    getString("encoder.error.tryAgain"),
                                    getString("encoder.error", e.getMessage()),
                                    JOptionPane.ERROR_MESSAGE);
                        });
                    } finally {
                        if ((fTemp != null) && (!fTemp.equals(inputFile))) {
                            fTemp.delete();
                        }
                        inputFile = fFile;
                    }
                    SwingUtilities.invokeLater(() -> {
                        progress.setVisible(false);
                        progressStep.setText(inputFile.getName());

                        btnRefreshImage.setEnabled(true);
                        btnSelectImage.setEnabled(true);
                        btnRefreshData.setEnabled(true);
                        updateSpace();
                    });
                }

            };
            runner.start();
        }
    }


    public void onAbout() {

        JDialog dlg = new JDialog(frame, getString("about.title"), true);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dlg.setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane();
        dlg.add(scroll, BorderLayout.CENTER);
        JPanel p = new JPanel();
        dlg.add(p, BorderLayout.NORTH);
        p.setLayout(new BorderLayout());
        JLabel img = new JLabel();
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("topsecret_logo.png");
            img.setIcon(new ImageIcon(ImageIO.read(is).getScaledInstance(200, 200, Image.SCALE_SMOOTH)));
            is.close();
        } catch (IOException e) {
        }
        p.add(img, BorderLayout.WEST);
        String sMsg = getString("about.message");
        String sHtml = "<html><div style='width:100%;text-align:center'>";
        sHtml += getString("about.message");
        sHtml += "<br/><br/>";
        sHtml += getString("about.author") + " Florent FRADET<br/><a href='mailto:top-secret.dao@ud.me'>top-secret.dao@ud.me</a>";
        sHtml += "<br/><br/>";
        sHtml += getString("about.link.github") + "<br/><a href='https://github.com/lolo77'>https://github.com/lolo77</a>";
        sHtml += "<br/><br/>";
        sHtml += getString("about.link.site") + "<br/><a href='http://top-secret.dao'>top-secret.dao</a>";
        sHtml += "<br/><br/>";
        sHtml += getString("about.link.telegram") + "<br/><a href='https://t.me/s/topsecret_projects'>topsecret_projects</a>";
        sHtml += "<br/><br/>";
        sHtml += getString("about.link.paypal") + "<br/><a href='https://www.paypal.com/donate/?hosted_button_id=BVBEEHRLYHFH6'>Paypal</a>";
        sHtml += "<br/><br/>";
        sHtml += "</div></html>";
        JEditorPane lblCopy = new JEditorPane("text/html", sHtml);
        p.add(lblCopy, BorderLayout.CENTER);
        lblCopy.setEditable(false);
        lblCopy.addHyperlinkListener(new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    try {
                        Desktop.getDesktop().browse(e.getURL().toURI());
                    } catch (IOException | URISyntaxException ex) {
                        // NO OP
                    }
                }
            }
        });
        lblCopy.setFont(getFont().deriveFont(Font.BOLD, 15));
        String s = "Visit *** https://github.com/lolo77 *** for more features !\n\n";
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("legal.txt");
            s += new String(HiUtils.readAllBytes(is));
            is.close();
        } catch (IOException e) {

        }
        JTextArea lbl = new JTextArea(s);
        lbl.setEditable(false);
        scroll.setViewportView(lbl);

        dlg.setSize(800, 600);
        dlg.toFront();
        dlg.setVisible(true);
    }


    private void addFilesToBag(List<File> lst) {
        Parameters p = buildParams(true);
        StringBuilder sb = new StringBuilder();

        for (File f : lst) {
            try {
                if ((f.isDirectory()) || (f.length() >= (1 << 24))) {
                    sb.append("\n- " + f.getName());
                    // 16Mo max
                    continue;
                }
                FileInputStream fis = new FileInputStream(f);
                byte buf[] = HiUtils.readAllBytes(fis);

                ChunkData data = new ChunkData();
                data.setData(buf);
                data.setName(f.getName());
                data.encryptData(p);

                bag.addItem(data);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (sb.length() > 0) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(MainPanel.this,
                        getString("import.err.add", sb.toString()),
                        getString("import.err.title"),
                        JOptionPane.ERROR_MESSAGE);
            });
        }
        TopEventDispatcher.getInstance().dispatch(new TopEventAttachmentChanged());
        refreshView();
        updateSpace();
    }


    private void handleDoubleClick(int row) {
        ChunkData _cd = null;
        if (row < 0) {
            // Create empty item
            _cd = new ChunkData();
            _cd.setName("new" + _cd.getId() + ".txt");
            bag.addItem(_cd);
            refreshView();
        } else {
            DataItem item = data.getLstItems().get(row);
            if (!item.isEncrypted()) {
                // View unencrypted item
                _cd = (ChunkData) bag.findById(item.getChunkDataId());
            }
        }
        final ChunkData cd = _cd;
        try {
            int netLen = cd.getData().length; // Length of the original data
            boolean bText = cd.getName().toLowerCase().endsWith(".txt");
            boolean bHtml = cd.getName().toLowerCase().endsWith(".html");
            if ((netLen < 1024 * 1024) && ((bText) || (bHtml))) {
                // View small text items (<1MiB) via a non-modal dialog
                String mime = (bHtml) ? "text/html" : "text/plain";
                JDialog dlg = new JDialog(frame, cd.getName(), false);
                dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                dlg.setLayout(new BorderLayout());
                JScrollPane scroll = new JScrollPane();
                dlg.add(scroll, BorderLayout.CENTER);
                String str = new String(cd.getData(), StandardCharsets.UTF_8);
                JEditorPane p = new JEditorPane(mime, str);
                p.setEditable(true);
                dlg.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        // Save the data into the ChunkData
                        cd.setData(p.getText().getBytes(StandardCharsets.UTF_8));
                        try {
                            bag.encryptAll(buildParams(true));
                        } catch (Exception ex) {
                            LOG.error("handleDoubleClick : Error while encrypting data : " + ex.getMessage());
                        }
                        refreshView();
                    }
                });
                scroll.setViewportView(p);
                dlg.setSize(600, 600);
                dlg.toFront();
                dlg.setVisible(true);
            } else {
                // View other items via the default navigator
                // --> avoid writing secret data to a temp file before opening the default app
                if (dataServer != null) {
                    dataServer.setData(cd.getData());
                    Desktop.getDesktop().browse(new URI("http://127.0.0.1:" + dataServer.getPort() + "/"));
                }
            }
        } catch (Exception ex) {
            LOG.error("handleDoubleClick : " + ex.getMessage());
        }
    }

    public void setInputImage(File img) {
        onRefreshImage(img);
        scrollPane.invalidate();
    }

    public void initAlgos() {
        String[] tabAlgos = new String[4];
        int idx = 0;
        tabAlgos[idx++] = "MD5";
        tabAlgos[idx++] = "SHA-256";
        tabAlgos[idx++] = "SHA-512";

        defaultAlgoIdx = 2;
        int iSel = defaultAlgoIdx;
        if (cfg.getAlgo() != null) {
            int i = 0;
            for (; i < idx; i++) {
                if (tabAlgos[i].equals(cfg.getAlgo())) {
                    iSel = i;
                    break;
                }
            }
            if (i == idx) {
                if (Utils.isAlgoSupported(cfg.getAlgo())) {
                    tabAlgos[idx++] = cfg.getAlgo();
                    iSel = i;
                }
            }
        }

        algo = new JComboBox<>(Arrays.copyOfRange(tabAlgos, 0, idx));
        algo.setSelectedIndex(iSel);
    }

    public Config getCfg() {
        return cfg;
    }


    public void initialize() {
        JPanel codecDecodePanel = new JPanel();
        codecDecodePanel.setLayout(new BoxLayout(codecDecodePanel, BoxLayout.X_AXIS));
        JLabel lblCodecDecoder = new JLabel(getString("lbl.codec.decode"));
        codecDecodePanel.add(lblCodecDecoder);
        cmbDecode = new JComboBox<>(Utils.asArray(HiDataStreamFactory.getListCodecsInput()));
        codecDecodePanel.add(cmbDecode);
        add(codecDecodePanel);

        JPanel credPanel = new JPanel(new SpringLayout());
        add(credPanel);
        JLabel lbAlgo = new JLabel(getString("label.hash"));
        credPanel.add(lbAlgo);
        JLabel lbPassMaster = new JLabel(getString("label.pass.master"));
        credPanel.add(lbPassMaster);
        JLabel lbPassData = new JLabel(getString("label.pass.data"));
        credPanel.add(lbPassData);

        JPanel paramPanel = new JPanel(new SpringLayout());
        add(paramPanel);
        JLabel lbBS = new JLabel(getString("label.bit.start"));
        paramPanel.add(lbBS);
        JLabel lbBE = new JLabel(getString("label.bit.extend"));
        paramPanel.add(lbBE);

        initAlgos();
        credPanel.add(algo);
        algo.setEditable(true);
        algo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String sAlgo = (String) algo.getSelectedItem();
                if (!Utils.isAlgoSupported(sAlgo)) {
                    JOptionPane.showMessageDialog(MainPanel.this, getString("hash.notSupported", sAlgo));
                    algo.setSelectedIndex(defaultAlgoIdx);
                }
            }
        });

        passMaster = new JPasswordField(10);
        credPanel.add(passMaster);

        passData = new JPasswordField(10);
        credPanel.add(passData);

        btnSelectImage = new JButton(getString("btn.image.select"));
        credPanel.add(btnSelectImage);
        btnSelectImage.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnSelectImage.addActionListener((actionEvent) -> {
            onOpenSource();
        });

        btnRefreshImage = new JButton(getString("btn.image.reload"));
        credPanel.add(btnRefreshImage);
        btnRefreshImage.setEnabled(false);
        btnRefreshImage.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onRefreshImage(inputFile);
            }
        });

        btnRefreshData = new JButton(getString("btn.data.reload"));
        credPanel.add(btnRefreshData);
        btnRefreshData.setEnabled(false);
        btnRefreshData.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onRefreshData();
            }
        });

        bitStart = new CoolJTextField(1);
        paramPanel.add(bitStart);
        Utils.addNumericValidator(bitStart, 0, 7);
        bitStart.setText(Integer.toString(cfg.getBitStart()));

        bitExtend = new JCheckBox();
        paramPanel.add(bitExtend);
        bitExtend.setSelected(cfg.isBitExt());

        SpringUtilities.makeGrid(credPanel,
                3, 3, //rows, cols
                5, 5, //initialX, initialY
                5, 5);//xPad, yPad

        SpringUtilities.makeGrid(paramPanel,
                2, 2, //rows, cols
                5, 5, //initialX, initialY
                5, 5);//xPad, yPad


        bitStart.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                validateInputs();
                updateSpace();
            }
        });

        bitExtend.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateSpace();
            }
        });

        // Advanced features
        // TODO : toggle visibility through an "advanced settings" accordion
        paramPanel.setVisible(false);

        JPanel panelSelect = new JPanel();
        add(panelSelect);
        panelSelect.setLayout(new BoxLayout(panelSelect, BoxLayout.Y_AXIS));


        progress = new JProgressBar();
        panelSelect.add(progress);
        progress.setAlignmentX(Component.CENTER_ALIGNMENT);
        progress.setVisible(false);

        progressStep = new JLabel();
        panelSelect.add(progressStep);
        progressStep.setAlignmentX(Component.CENTER_ALIGNMENT);
        progressStep.setVisible(false);

        img = new JLabel();
        panelSelect.add(img);
        img.setAlignmentX(Component.CENTER_ALIGNMENT);
        img.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        img.setDropTarget(new DropTarget() {
            public synchronized void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>)
                            evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (droppedFiles.size() > 0) {
                        setInputImage(droppedFiles.get(0));
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        JLabel lblDragInTable = new JLabel(getString("label.dnd.table"));
        panelSelect.add(lblDragInTable);
        lblDragInTable.setAlignmentX(Component.CENTER_ALIGNMENT);

        tableData = new JTable(data);
        data.setParent(this);
        scrollPane = new JScrollPane(tableData);
        tableData.setFillsViewportHeight(true);
        tableData.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        panelSelect.add(scrollPane);
        scrollPane.setAlignmentX(Component.CENTER_ALIGNMENT);
        scrollPane.setPreferredSize(new Dimension(230, 100));

        tableData.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    // Get the row and column at the mouse cursor
                    int row = tableData.rowAtPoint(e.getPoint());
                    handleDoubleClick(row);
                }
            }
        });

        ListSelectionModel selectionModel = tableData.getSelectionModel();

        selectionModel.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                btnDelete.setEnabled(!tableData.getSelectionModel().isSelectionEmpty());
            }
        });

        tableData.getColumnModel().getColumn(0).setPreferredWidth(100);
        tableData.getColumnModel().getColumn(1).setPreferredWidth(25);

        tableData.setDropTarget(new DropTarget() {
            public synchronized void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>) evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    addFilesToBag(droppedFiles);
                    refreshView();
                    scrollPane.invalidate();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        tableData.setDefaultRenderer(String.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                DataItem item = data.getLstItems().get(row);
                if (item.isEncrypted()) {
                    Font f = getFont().deriveFont(Font.ITALIC);
                    setFont(f);
                    setForeground(Color.RED);
                } else {
                    setForeground(Color.BLACK);
                }
                return c;
            }
        });

        JPanel codecEncodePanel = new JPanel();
        codecEncodePanel.setLayout(new BoxLayout(codecEncodePanel, BoxLayout.X_AXIS));
        JLabel lblCodecEncoder = new JLabel(getString("lbl.codec.encode"));
        codecEncodePanel.add(lblCodecEncoder);
        cmbEncode = new JComboBox<>(Utils.asArray(HiDataStreamFactory.getListCodecsOutput()));
        codecEncodePanel.add(cmbEncode);
        panelSelect.add(codecEncodePanel);
        cmbEncode.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                TopEventDispatcher.getInstance().dispatch(new TopEventEvaluateSpace(inputFile, (String) cmbEncode.getSelectedItem()));
            }
        });

        JPanel panelBtn = new JPanel();
        panelBtn.setLayout(new FlowLayout());
        panelSelect.add(panelBtn);
        panelBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        btnNew = new JButton(getString("btn.data.new"));
        panelBtn.add(btnNew);
        btnNew.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnNew.addActionListener(e -> {
            handleDoubleClick(-1);
        });

        btnDelete = new JButton(getString("btn.data.delete"));
        panelBtn.add(btnDelete);
        btnDelete.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnDelete.setEnabled(false);
        btnDelete.addActionListener(e -> {
            if (tableData.isEditing()) {
                tableData.editingCanceled(null);
            }
            for (int row = 0; row < data.getLstItems().size(); row++) {
                if (tableData.getSelectionModel().isSelectedIndex(row)) {
                    DataItem d = data.getLstItems().get(row);
                    bag.removeById(d.getChunkDataId());
                }
            }

            refreshView();

            btnDelete.setEnabled(false);
        });

        JPanel panelBtn2 = new JPanel();
        panelBtn2.setLayout(new BorderLayout(5, 5));

        btnEncode = new JButton(getString("btn.encode"));
        panelBtn2.add(btnEncode, BorderLayout.NORTH);
        btnEncode.setEnabled(false);
        btnEncode.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onEncode(false);
            }
        });

        btnEncodeTo = new JButton(getString("btn.encode.to"));
        panelBtn2.add(btnEncodeTo, BorderLayout.SOUTH);
        btnEncodeTo.setEnabled(false);
        btnEncodeTo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onEncode(true);
            }
        });

        panelBtn.add(panelBtn2);

        JPanel panelBtn3 = new JPanel();
        panelBtn3.setLayout(new BorderLayout(5, 5));

        btnDecodeTo = new JButton(getString("btn.decode"));
        panelBtn3.add(btnDecodeTo, BorderLayout.NORTH);
        btnDecodeTo.setEnabled(false);
        btnDecodeTo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onDecode();
            }
        });

        JButton btnExport = new JButton(getString("btn.export"));
        panelBtn3.add(btnExport, BorderLayout.SOUTH);
        btnExport.setVisible(false);
        btnExport.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onExport();
            }
        });


        panelBtn.add(panelBtn3);


        lblSpaceTotal = new JLabel();
        panelSelect.add(lblSpaceTotal);
        lblSpaceTotal.setAlignmentX(Component.CENTER_ALIGNMENT);

        lblSpaceUsed = new JLabel();
        panelSelect.add(lblSpaceUsed);
        lblSpaceUsed.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblSpaceUsed.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));


        lblSpaceFree = new JLabel();
        panelSelect.add(lblSpaceFree);
        lblSpaceFree.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblSpaceFree.setBorder(BorderFactory.createEmptyBorder(2, 0, 5, 0));

        lblAltFact = new JLabel();
        panelSelect.add(lblAltFact);
        lblAltFact.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblAltFact.setOpaque(true);
        lblAltFact.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JButton btnAbout = new JButton(getString("btn.about"));
        panelSelect.add(btnAbout);
        btnAbout.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnAbout.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        btnAbout.setFont(btnAbout.getFont().deriveFont(Font.BOLD, 12));
        btnAbout.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onAbout();
            }
        });


        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("topsecret_logo.png");
            replaceImg(ImageIO.read(is));
            is.close();
            progressStep.setVisible(true);
            progressStep.setText(getString("label.dnd.image"));
        } catch (
                IOException e) {
            e.printStackTrace();
        }

        TopEventDispatcher.getInstance().addListener(this);
        refreshView();
    }


    public void loadConfig() {
        cfg = new Config();
        try {
            File f = new File("config.xml");
            XMLDecoder xd = new XMLDecoder(new FileInputStream(f));
            cfg = (Config) xd.readObject();
            xd.close();

            // Ensure the frame is visible
            // (i.e. cfg has coordinates on a disconnected screen on a multiple-screens desktop)
            boolean bVisible = false;
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice[] screens = ge.getScreenDevices();
            for (GraphicsDevice screen : screens) {
                Rectangle screenBounds = screen.getDefaultConfiguration().getBounds();
                bVisible |= screenBounds.contains(cfg.getFrameRect());
            }
            if (!bVisible) {
                // Reset position to main screen
                cfg.getFrameRect().move(0, 0);
            }
            getParent().getParent().getParent().getParent().setBounds(cfg.getFrameRect());
        } catch (Exception e) {
            // NO OP
            cfg.setFrameRect(null);
            cfg.setAlgo(null);
            cfg.setBitStart(0);
            cfg.setBitExt(true);
            cfg.setLastOpenDir(".");
        }

        if (cfg.getLang() == null) {
            JDialog dlg = new JDialog();
            dlg.setModal(true);
            dlg.setTitle("Choose your language");
            dlg.setSize(200, 70);
            dlg.setIconImage(frame.getIconImage());
            dlg.setLayout(new BorderLayout());
            JComboBox<Locale> cmb = new JComboBox<>();
            cmb.addItem(Locale.forLanguageTag("fr-FR"));
            cmb.addItem(Locale.forLanguageTag("en-US"));
            cmb.setRenderer(new ListCellRenderer<Locale>() {
                @Override
                public Component getListCellRendererComponent(JList<? extends Locale> list, Locale value, int index, boolean isSelected, boolean cellHasFocus) {
                    return new JLabel(value.getDisplayName());
                }
            });
            dlg.add(cmb, BorderLayout.CENTER);
            JButton btnOk = new JButton("OK");
            btnOk.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    dlg.dispose();
                }
            });
            dlg.add(btnOk, BorderLayout.EAST);
            dlg.setVisible(true);
            // Modal dlg
            Locale loc = (Locale) cmb.getSelectedItem();
            cfg.setLang(loc.getLanguage() + "-" + loc.getCountry());
        }
        Locale.setDefault(Locale.forLanguageTag(cfg.getLang()));
        try {
            bundle = ResourceBundle.getBundle("messages");
        } catch (Exception e) {
            Locale.setDefault(Locale.forLanguageTag("en-US"));
            bundle = ResourceBundle.getBundle("messages");
        }
    }


    public void saveConfig() {
        File f = new File("config.xml");

        cfg.setAlgo((String) algo.getSelectedItem());
        cfg.setBitExt(bitExtend.isSelected());
        cfg.setBitStart(Integer.parseInt(bitStart.getText()));
        Container frame = getParent().getParent().getParent().getParent();
        cfg.setFrameRect(frame.getBounds());
        try {
            XMLEncoder xe = new XMLEncoder(new FileOutputStream(f));
            xe.writeObject(cfg);
            xe.close();
        } catch (Exception e) {
            e.printStackTrace();
            // NO OP
        }
    }
}
