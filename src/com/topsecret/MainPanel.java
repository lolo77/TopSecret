package com.topsecret;

import com.secretlib.exception.NoBagException;
import com.secretlib.exception.TruncatedBagException;
import com.secretlib.io.stream.HiDataAbstractInputStream;
import com.secretlib.io.stream.HiDataAbstractOutputStream;
import com.secretlib.io.stream.HiDataStreamFactory;
import com.secretlib.model.*;
import com.secretlib.util.HiUtils;
import com.secretlib.util.Parameters;
import com.topsecret.model.DataItem;
import com.topsecret.model.DataModel;
import com.topsecret.util.Config;
import com.topsecret.util.CoolJTextField;
import com.topsecret.util.SpringUtilities;
import com.topsecret.util.Utils;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.*;
import java.text.MessageFormat;
import java.util.List;
import java.util.*;


/**
 * @author Florent FRADET
 * <p>
 * Main Swing GUI class
 */
public class MainPanel extends JPanel {

    private final static int IMAGE_SIZE = 100;

    ResourceBundle bundle;
    JFrame frame;

    JButton btnSelectImage;
    JButton btnRefreshImage;
    JButton btnRefreshData;
    JButton btnEncode;
    JButton btnDecode;
    JButton btnDelete;

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

    JScrollPane scrollPane;
    JTable tableData;
    private final DataModel data = new DataModel(this);

    private Config cfg = new Config();
    private int defaultAlgoIdx;
    private HiDataBag bag = new HiDataBag();
    private int iBitStart;
    private File inputFile = null;

    private int[] spaceCapacity = new int[8];


    public MainPanel(JFrame frame) {
        this.frame = frame;
        Arrays.fill(spaceCapacity, 0);
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


    private Parameters buildParams() {
        Parameters p = new Parameters();
        ProgressCB progCB = new ProgressCB();
        p.setProgressCallBack(progCB);
        p.setKm(String.copyValueOf(passMaster.getPassword()));
        p.setKd(String.copyValueOf(passData.getPassword()));
        p.setHashAlgo((String) algo.getSelectedItem());
        p.setBitStart(iBitStart);
        p.setAutoExtendBit(bitExtend.isSelected());

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
        long total = getSpaceCapacity() / 8; // bytes
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
        btnDecode.setEnabled(b && (hasUnencryptedData()));

        updateAlterationFactor();
    }


    public void refreshView() {
        data.getLstItems().clear();

        for (int i = 0; i < bag.getItems().size(); i++) {
            AbstractChunk df = bag.getItems().get(i);

            if (EChunk.DATA.equals(df.getType())) {
                ChunkData dfd = (ChunkData) df;
                DataItem line = new DataItem(bag, dfd.getName(), dfd.getLength(), dfd.isEncrypted(), dfd.getId());

                data.getLstItems().add(line);
            }
        }
        scrollPane.invalidate();
        tableData.getSelectionModel().clearSelection();
        updateSpace();
        scrollPane.repaint();
    }


    private class ProgressCB implements IProgressCallback {

        private final HashMap<ProgressStepEnum, String> msgs = new HashMap<>();
        private ProgressMessage lastMsg = null;

        public ProgressCB() {
            msgs.put(ProgressStepEnum.DECODE, getString("process.decode"));
            msgs.put(ProgressStepEnum.ENCODE, getString("process.encode"));
            msgs.put(ProgressStepEnum.READ, getString("process.read"));
            msgs.put(ProgressStepEnum.WRITE, getString("process.write"));
        }

        @Override
        public void update(ProgressMessage progressMessage) {
            lastMsg = progressMessage;

            SwingUtilities.invokeLater(() -> {
                if (!progress.isVisible()) {
                    progress.setVisible(true);
                }
                progress.setValue((int) (progressMessage.getProgress() * 100.0));
                progressStep.setText(msgs.get(progressMessage.getStep()));
            });
        }

        public ProgressMessage getLastMsg() {
            return lastMsg;
        }
    }


    private void loadSource(File file, boolean verbose) throws TruncatedBagException {
        Parameters p = buildParams();

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
            HiDataBag newBag = hdis.getBag();
            if (!newBag.isEmpty()) {
                // Replace the displayed bag by the one found in the source image
                bag = newBag;
                bag.decryptAll(p);

                if (verbose) {
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
            if (verbose) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(MainPanel.this,
                            getString("input.info.notFound"),
                            getString("input.title"),
                            JOptionPane.INFORMATION_MESSAGE);
                });
            }
        } catch (Exception e) {
            if (verbose) {
                JOptionPane.showMessageDialog(MainPanel.this,
                        getString("input.err.file.data", e.getMessage()),
                        getString("input.title"),
                        JOptionPane.ERROR_MESSAGE);
            }
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
            refreshView();
            ProgressCB progCB = (ProgressCB) p.getProgressCallBack();
            if (progCB.getLastMsg() != null) {
                spaceCapacity = progCB.getLastMsg().getNbBitsTotals();
                updateSpace();
            }
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
            onRefreshImage(file, true);
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


    public void onRefreshImage(File file, boolean verbose) {
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
        btnDecode.setEnabled(false);
        btnDelete.setEnabled(false);

        new Thread() {
            public void run() {
                try {
                    SwingUtilities.invokeLater(() -> {
                        try {
                            BufferedImage bi = ImageIO.read(file);
                            replaceImg(bi);
                        } catch (IOException e) {
                            // NO OP
                        }
                    });
                    loadSource(file, verbose);
                    if (verbose) {
                        cfg.updateLastOpenDir(file);
                        inputFile = file;
                    }
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        if (verbose) {
                            String msg = (e.getMessage() != null) ? getString("input.err.file.data", e.getMessage()) : getString("input.err.file.data");
                            if (e instanceof TruncatedBagException) {
                                msg = getString("input.err.trunc");
                            }
                            JOptionPane.showMessageDialog(MainPanel.this,
                                    msg,
                                    getString("input.title"),
                                    JOptionPane.ERROR_MESSAGE);
                        }
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
        Parameters p = buildParams();
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
            if (fDir.isDirectory()) {
                dirChooser.setCurrentDirectory(fDir);
            }
        }
        dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        // Show save file dialog
        int res = dirChooser.showOpenDialog(this);

        if (res == JFileChooser.APPROVE_OPTION) {
            File file = dirChooser.getSelectedFile();
            Parameters p = buildParams();

            btnRefreshImage.setEnabled(false);
            btnSelectImage.setEnabled(false);
            btnRefreshData.setEnabled(false);
            btnEncode.setEnabled(false);
            btnDecode.setEnabled(false);
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
                        final int nb = nbData;
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(MainPanel.this,
                                    getString("decoder.extract", nb, file.getAbsolutePath()),
                                    getString("decoder.success"),
                                    JOptionPane.INFORMATION_MESSAGE);
                        });

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


    public void onEncode() {

        if (!validateInputs()) {
            return;
        }

        if (inputFile != null) {

            SwingUtilities.invokeLater(() -> {
                btnRefreshImage.setEnabled(false);
                btnSelectImage.setEnabled(false);
                btnRefreshData.setEnabled(false);
                btnEncode.setEnabled(false);
                btnDecode.setEnabled(false);
                btnDelete.setEnabled(false);

                progress.setMinimum(0);
                progress.setMaximum(100);
                progress.setValue(0);
                progressStep.setVisible(true);
            });

            Thread runner = new Thread() {
                public void run() {
                    try {
                        Parameters p = buildParams();
                        String sExt = Utils.getFileExt(inputFile);
                        File fTemp = new File(inputFile.getAbsolutePath() + ".tmp");
                        // in and out must not be the same file or the input file would be squizzed (length set to 0).
                        FileInputStream fis = new FileInputStream(inputFile);
                        FileOutputStream fos = new FileOutputStream(fTemp);
                        HiDataAbstractOutputStream out = HiDataStreamFactory.createOutputStream(fis, fos, p, sExt);
                        if (out == null)
                            throw new Exception(getString("encoder.error.ext", sExt));
                        bag.encryptAll(p);
                        out.write(bag.toByteArray());
                        out.close();
                        inputFile.delete();
                        fTemp.renameTo(inputFile);
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(MainPanel.this,
                                    getString("encoder.error.tryAgain"),
                                    getString("encoder.error"),
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
            InputStream is = getClass().getClassLoader().getResourceAsStream("logoflo.png");
            img.setIcon(new ImageIcon(ImageIO.read(is)));
            is.close();
        } catch (IOException e) {
        }
        p.add(img, BorderLayout.WEST);
        String sMsg = getString("about.message");
        JLabel lblCopy = new JLabel("<html><div style='width:100%;text-align:center'>" + sMsg + "<br/><br/>" + getString("about.author") + " Florent FRADET<br/><br/>" + getString("about.link") + "<br/>https://github.com/lolo77</div></html>");
        p.add(lblCopy, BorderLayout.CENTER);
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

        dlg.setSize(600, 600);
        dlg.toFront();
        dlg.setVisible(true);
    }


    private void addFilesToBag(List<File> lst) {
        Parameters p = buildParams();
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

        refreshView();
        updateSpace();
    }


    public void initialize() {
        loadConfig();

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


        algo = new JComboBox<>(Arrays.copyOfRange(tabAlgos, 0, idx));
        credPanel.add(algo);
        algo.setSelectedIndex(iSel);
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
                onRefreshImage(inputFile, true);
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

        paramPanel.setVisible(false);

        JPanel panelSelect = new JPanel();
        add(panelSelect);
        BoxLayout bly = new BoxLayout(panelSelect, BoxLayout.Y_AXIS);
        panelSelect.setLayout(bly);


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
                        onRefreshImage(droppedFiles.get(0), true);
                        scrollPane.invalidate();
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
                    List<File> droppedFiles = (List<File>)
                            evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
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
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
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

        JPanel panelBtn = new JPanel();
        panelBtn.setLayout(new FlowLayout());
        panelSelect.add(panelBtn);
        panelBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        btnDelete = new JButton(getString("btn.data.delete"));
        panelBtn.add(btnDelete);
        btnDelete.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnDelete.setEnabled(false);
        btnDelete.addActionListener(e -> {

            for (int row = 0; row < data.getLstItems().size(); row++) {
                if (tableData.getSelectionModel().isSelectedIndex(row)) {
                    DataItem d = data.getLstItems().get(row);
                    bag.removeById(d.getChunkDataId());
                }
            }

            refreshView();

            btnDelete.setEnabled(false);
        });

        btnEncode = new JButton(getString("btn.encode"));
        panelBtn.add(btnEncode);
        btnEncode.setEnabled(false);
        btnEncode.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onEncode();
            }
        });

        btnDecode = new JButton(getString("btn.decode"));
        panelBtn.add(btnDecode);
        btnDecode.setEnabled(false);
        btnDecode.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onDecode();
            }
        });

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
            InputStream is = getClass().getClassLoader().getResourceAsStream("logoflo.png");
            replaceImg(ImageIO.read(is));
            is.close();
            progressStep.setVisible(true);
            progressStep.setText(getString("label.dnd.image"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        refreshView();
    }


    public void loadConfig() {
        cfg = new Config();
        try {
            File f = new File("config.xml");
            XMLDecoder xd = new XMLDecoder(new FileInputStream(f));
            cfg = (Config) xd.readObject();
            xd.close();

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
