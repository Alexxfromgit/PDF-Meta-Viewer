package gui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import extractor.PDFExtractor;
import extractor.PdfExtractionException;
import repository.DocumentRecord;
import repository.DocumentRepository;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.RowFilter;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

import static utilities.PdfMetaViewerConstants.APP_MAIN_TITLE;
import static utilities.PdfMetaViewerConstants.HEIGHT_APP;
import static utilities.PdfMetaViewerConstants.WIDTH_APP;

/**
 * Main application window for browsing PDF metadata.
 */
public final class MetaViewerForm extends JFrame {
    private static final String CARD_WELCOME = "welcome";
    private static final String CARD_LOADING = "loading";
    private static final String CARD_RESULTS = "results";
    private static final String PREF_DARK_MODE = "darkMode";
    private static final String PREF_LAST_DIRECTORY = "lastDirectory";
    private static final String PREF_WIDTH = "windowWidth";
    private static final String PREF_HEIGHT = "windowHeight";
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(MetaViewerForm.class);
    private static final Color ACCENT = new Color(206, 62, 67);

    private final PDFExtractor extractor = new PDFExtractor();
    private final MetadataTableModel metadataModel = new MetadataTableModel();
    private final TableRowSorter<MetadataTableModel> metadataSorter = new TableRowSorter<>(metadataModel);
    private final CardLayout contentLayout = new CardLayout();
    private final JPanel contentCards = new JPanel(contentLayout);

    private JButton openButton;
    private JButton exportButton;
    private JButton themeButton;
    private JButton copyButton;
    private JTextField searchField;
    private JTable metadataTable;
    private JTextArea textPreview;
    private JLabel fileNameLabel;
    private JLabel filePathLabel;
    private JLabel fileSizeLabel;
    private JLabel metadataCountLabel;
    private JLabel pageCountLabel;
    private JLabel previewNoteLabel;
    private JLabel statusLabel;
    private JProgressBar statusProgress;
    private JTabbedPane resultTabs;
    private JScrollPane metadataScrollPane;
    private JScrollPane textScrollPane;
    private JPanel statusBar;

    private DocumentRecord documentRecord;
    private SwingWorker<DocumentRecord, Void> extractionWorker;
    private boolean darkMode;
    private Status currentStatus = Status.NEUTRAL;
    private String currentStatusMessage = "";

    public MetaViewerForm() {
        super(APP_MAIN_TITLE);
        darkMode = PREFERENCES.getBoolean(PREF_DARK_MODE, false);
        configureLookAndFeelDefaults();
        setIconImage(createAppIcon(64));
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(820, 560));
        setContentPane(buildRoot());
        configureWindow();
        installKeyboardActions();
        installFileDrop();
        updateThemeButton();
        showWelcome();
    }

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", APP_MAIN_TITLE);
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("sun.java2d.uiScale.enabled", "true");

        SwingUtilities.invokeLater(() -> {
            boolean useDarkTheme = PREFERENCES.getBoolean(PREF_DARK_MODE, false);
            if (useDarkTheme) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
            configureLookAndFeelDefaults();
            MetaViewerForm application = new MetaViewerForm();
            application.setVisible(true);
            if (args.length > 0 && !args[0].isBlank()) {
                application.openPdf(Path.of(args[0]));
            }
        });
    }

    private static void configureLookAndFeelDefaults() {
        UIManager.put("Component.arc", 12);
        UIManager.put("Button.arc", 12);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("ProgressBar.arc", 999);
        UIManager.put("ScrollBar.width", 11);
        UIManager.put("TabbedPane.showTabSeparators", false);
        UIManager.put("TabbedPane.tabHeight", 40);
        UIManager.put("Table.rowHeight", 38);
    }

    private JPanel buildRoot() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder());
        root.add(buildHeader(), BorderLayout.NORTH);

        contentCards.setBorder(BorderFactory.createEmptyBorder(2, 30, 22, 30));
        contentCards.add(buildWelcomePanel(), CARD_WELCOME);
        contentCards.add(buildLoadingPanel(), CARD_LOADING);
        contentCards.add(buildResultsPanel(), CARD_RESULTS);
        root.add(contentCards, BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setBorder(BorderFactory.createEmptyBorder(22, 30, 18, 30));

        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.X_AXIS));
        brand.add(new AppMark(44));
        brand.add(Box.createHorizontalStrut(13));

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(APP_MAIN_TITLE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JLabel subtitle = mutedLabel("Inspect. Search. Export.");
        subtitle.setFont(subtitle.getFont().deriveFont(12f));
        titles.add(title);
        titles.add(Box.createVerticalStrut(2));
        titles.add(subtitle);
        brand.add(titles);
        header.add(brand, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 9, 0));
        actions.setOpaque(false);
        themeButton = secondaryButton("");
        themeButton.addActionListener(event -> toggleTheme());
        themeButton.setToolTipText("Switch between light and dark appearance");
        themeButton.getAccessibleContext().setAccessibleDescription("Toggle application appearance");

        exportButton = secondaryButton("Export JSON");
        exportButton.addActionListener(event -> exportMetadata());
        exportButton.setEnabled(false);
        exportButton.setToolTipText("Export the current metadata as formatted JSON");

        openButton = primaryButton("Open PDF");
        openButton.addActionListener(event -> choosePdf());
        openButton.setToolTipText("Open a PDF file (Ctrl/Command + O)");

        actions.add(themeButton);
        actions.add(exportButton);
        actions.add(openButton);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JPanel buildWelcomePanel() {
        RoundedPanel welcome = new RoundedPanel(22);
        welcome.setLayout(new GridBagLayout());
        welcome.setBorder(BorderFactory.createEmptyBorder(38, 38, 38, 38));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.insets = new Insets(0, 0, 20, 0);
        welcome.add(new DocumentIllustration(116), constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 8, 0);
        JLabel heading = new JLabel("See what your PDF says about itself");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 24f));
        welcome.add(heading, constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 24, 0);
        JLabel detail = mutedLabel("Open a file to inspect every metadata field and preview its extracted text.");
        detail.setFont(detail.getFont().deriveFont(14f));
        welcome.add(detail, constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 11, 0);
        JButton welcomeOpenButton = primaryButton("Choose a PDF");
        welcomeOpenButton.addActionListener(event -> choosePdf());
        welcomeOpenButton.setPreferredSize(new Dimension(156, 42));
        welcome.add(welcomeOpenButton, constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 0, 0);
        JLabel dropHint = mutedLabel("or drop one anywhere in this window");
        dropHint.setFont(dropHint.getFont().deriveFont(12f));
        welcome.add(dropHint, constraints);
        return welcome;
    }

    private JPanel buildLoadingPanel() {
        RoundedPanel loading = new RoundedPanel(22);
        loading.setLayout(new GridBagLayout());
        loading.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.insets = new Insets(0, 0, 22, 0);
        loading.add(new AppMark(58), constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 8, 0);
        JLabel title = new JLabel("Reading document details…");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        loading.add(title, constraints);

        constraints.gridy++;
        constraints.insets = new Insets(0, 0, 20, 0);
        loading.add(mutedLabel("Large or image-heavy PDFs can take a little longer."), constraints);

        constraints.gridy++;
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setPreferredSize(new Dimension(280, 7));
        loading.add(progress, constraints);
        return loading;
    }

    private JPanel buildResultsPanel() {
        JPanel results = new JPanel(new BorderLayout(0, 14));
        results.setOpaque(false);
        results.add(buildFileSummary(), BorderLayout.NORTH);
        results.add(buildResultTabs(), BorderLayout.CENTER);
        return results;
    }

    private JPanel buildFileSummary() {
        RoundedPanel summary = new RoundedPanel(18);
        summary.setLayout(new BorderLayout(16, 0));
        summary.setBorder(BorderFactory.createEmptyBorder(17, 20, 17, 20));
        summary.add(new AppMark(38), BorderLayout.WEST);

        JPanel identity = new JPanel();
        identity.setOpaque(false);
        identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
        fileNameLabel = new JLabel();
        fileNameLabel.setFont(fileNameLabel.getFont().deriveFont(Font.BOLD, 15f));
        filePathLabel = mutedLabel("");
        filePathLabel.setFont(filePathLabel.getFont().deriveFont(11f));
        identity.add(fileNameLabel);
        identity.add(Box.createVerticalStrut(3));
        identity.add(filePathLabel);
        summary.add(identity, BorderLayout.CENTER);

        JPanel facts = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 0));
        facts.setOpaque(false);
        fileSizeLabel = factLabel("Size", "—");
        pageCountLabel = factLabel("Pages", "—");
        metadataCountLabel = factLabel("Fields", "—");
        facts.add(fileSizeLabel);
        facts.add(pageCountLabel);
        facts.add(metadataCountLabel);
        summary.add(facts, BorderLayout.EAST);
        return summary;
    }

    private JTabbedPane buildResultTabs() {
        resultTabs = new JTabbedPane();
        resultTabs.putClientProperty("JTabbedPane.tabAreaAlignment", "leading");
        resultTabs.addTab("Metadata", buildMetadataTab());
        resultTabs.addTab("Text preview", buildTextTab());
        return resultTabs;
    }

    private JPanel buildMetadataTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 4, 2, 4));

        JPanel tools = new JPanel(new BorderLayout(10, 0));
        tools.setOpaque(false);
        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search properties or values");
        searchField.putClientProperty("JTextField.leadingIcon", new SearchIcon());
        searchField.setPreferredSize(new Dimension(320, 36));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updateMetadataFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updateMetadataFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updateMetadataFilter();
            }
        });
        tools.add(searchField, BorderLayout.WEST);

        copyButton = secondaryButton("Copy selected");
        copyButton.setEnabled(false);
        copyButton.addActionListener(event -> copySelectedMetadata());
        tools.add(copyButton, BorderLayout.EAST);
        panel.add(tools, BorderLayout.NORTH);

        metadataTable = new JTable(metadataModel);
        metadataTable.setRowSorter(metadataSorter);
        metadataTable.setAutoCreateRowSorter(false);
        metadataTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        metadataTable.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        metadataTable.setShowVerticalLines(false);
        metadataTable.setIntercellSpacing(new Dimension(0, 0));
        metadataTable.getColumnModel().getColumn(0).setPreferredWidth(260);
        metadataTable.getColumnModel().getColumn(0).setMaxWidth(380);
        metadataTable.getColumnModel().getColumn(1).setPreferredWidth(660);
        metadataTable.getSelectionModel().addListSelectionListener(event ->
                copyButton.setEnabled(!metadataTable.getSelectionModel().isSelectionEmpty()));
        metadataTable.setDefaultRenderer(String.class, new MetadataCellRenderer());
        metadataTable.getTableHeader().setReorderingAllowed(false);
        metadataTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && event.getButton() == MouseEvent.BUTTON1) {
                    copySelectedMetadata();
                }
            }
        });

        metadataScrollPane = new JScrollPane(metadataTable);
        metadataScrollPane.setBorder(BorderFactory.createLineBorder(borderColor(), 1, true));
        metadataScrollPane.getViewport().setBackground(metadataTable.getBackground());
        panel.add(metadataScrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildTextTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 9));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 4, 2, 4));
        textPreview = new JTextArea();
        textPreview.setEditable(false);
        textPreview.setLineWrap(true);
        textPreview.setWrapStyleWord(true);
        textPreview.setMargin(new Insets(16, 18, 16, 18));
        textPreview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        textPreview.getAccessibleContext().setAccessibleName("Extracted PDF text preview");

        textScrollPane = new JScrollPane(textPreview);
        textScrollPane.setBorder(BorderFactory.createLineBorder(borderColor(), 1, true));
        textScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(textScrollPane, BorderLayout.CENTER);

        previewNoteLabel = mutedLabel("");
        previewNoteLabel.setFont(previewNoteLabel.getFont().deriveFont(11f));
        panel.add(previewNoteLabel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildStatusBar() {
        statusBar = new JPanel(new BorderLayout(10, 0));
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, borderColor()),
                BorderFactory.createEmptyBorder(10, 30, 11, 30)
        ));
        statusLabel = new JLabel();
        statusLabel.setFont(statusLabel.getFont().deriveFont(12f));
        statusBar.add(statusLabel, BorderLayout.WEST);

        statusProgress = new JProgressBar();
        statusProgress.setIndeterminate(true);
        statusProgress.setPreferredSize(new Dimension(96, 5));
        statusProgress.setVisible(false);
        statusBar.add(statusProgress, BorderLayout.EAST);
        return statusBar;
    }

    private void configureWindow() {
        int width = Math.max(getMinimumSize().width, PREFERENCES.getInt(PREF_WIDTH, WIDTH_APP));
        int height = Math.max(getMinimumSize().height, PREFERENCES.getInt(PREF_HEIGHT, HEIGHT_APP));
        setSize(width, height);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                PREFERENCES.putInt(PREF_WIDTH, getWidth());
                PREFERENCES.putInt(PREF_HEIGHT, getHeight());
            }
        });
    }

    private void installKeyboardActions() {
        int shortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        registerShortcut("openPdf", KeyStroke.getKeyStroke(KeyEvent.VK_O, shortcutMask), this::choosePdf);
        registerShortcut("exportJson", KeyStroke.getKeyStroke(KeyEvent.VK_E, shortcutMask), this::exportMetadata);
        registerShortcut("findMetadata", KeyStroke.getKeyStroke(KeyEvent.VK_F, shortcutMask), () -> {
            if (documentRecord != null) {
                resultTabs.setSelectedIndex(0);
                searchField.requestFocusInWindow();
                searchField.selectAll();
            }
        });
        registerShortcut("clearSearch", KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), () -> {
            if (searchField != null && !searchField.getText().isEmpty()) {
                searchField.setText("");
            }
        });
    }

    private void registerShortcut(String name, KeyStroke keyStroke, Runnable action) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }

    private void installFileDrop() {
        getRootPane().setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                boolean supported = support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
                if (supported && support.isDrop()) {
                    support.setDropAction(TransferHandler.COPY);
                }
                return supported;
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                try {
                    List<File> files = (List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    if (files.size() != 1) {
                        showError("Drop one PDF at a time.", null);
                        return false;
                    }
                    openPdf(files.get(0).toPath());
                    return true;
                } catch (Exception exception) {
                    showError("That file could not be opened.", exception);
                    return false;
                }
            }
        });
    }

    private void choosePdf() {
        if (isMacOs()) {
            choosePdfWithNativeDialog();
            return;
        }

        JFileChooser chooser = new JFileChooser(lastDirectory());
        chooser.setDialogTitle("Choose a PDF to inspect");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("PDF documents (*.pdf)", "pdf"));
        chooser.setMultiSelectionEnabled(false);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            rememberDirectory(chooser.getSelectedFile().toPath());
            openPdf(chooser.getSelectedFile().toPath());
        }
    }

    private void choosePdfWithNativeDialog() {
        FileDialog chooser = new FileDialog(this, "Choose a PDF to inspect", FileDialog.LOAD);
        File directory = lastDirectory();
        if (directory != null) {
            chooser.setDirectory(directory.getAbsolutePath());
        }
        chooser.setFilenameFilter((folder, name) -> name.toLowerCase(Locale.ROOT).endsWith(".pdf"));
        chooser.setMultipleMode(false);
        chooser.setVisible(true);

        if (chooser.getFile() != null) {
            Path selectedFile = Path.of(chooser.getDirectory(), chooser.getFile());
            rememberDirectory(selectedFile);
            openPdf(selectedFile);
        }
    }

    private void openPdf(Path file) {
        if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            showError("Choose a file with a .pdf extension.", null);
            return;
        }
        if (extractionWorker != null && !extractionWorker.isDone()) {
            extractionWorker.cancel(true);
        }

        documentRecord = null;
        exportButton.setEnabled(false);
        searchField.setText("");
        contentLayout.show(contentCards, CARD_LOADING);
        setBusy(true, "Analyzing " + file.getFileName() + "…");

        extractionWorker = new SwingWorker<>() {
            @Override
            protected DocumentRecord doInBackground() throws PdfExtractionException {
                return extractor.extract(file);
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                setBusy(false, "");
                try {
                    showDocument(get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showWelcome();
                    showError("PDF analysis was interrupted.", exception);
                } catch (ExecutionException exception) {
                    showWelcome();
                    Throwable cause = exception.getCause();
                    String message = cause instanceof PdfExtractionException
                            ? cause.getMessage()
                            : "The PDF could not be analyzed.";
                    showError(message, cause);
                }
            }
        };
        extractionWorker.execute();
    }

    private void showDocument(DocumentRecord record) {
        documentRecord = record;
        metadataModel.setMetadata(record.getMetadata());
        metadataSorter.setSortKeys(List.of(new javax.swing.RowSorter.SortKey(
                0, javax.swing.SortOrder.ASCENDING)));
        textPreview.setText(record.getContent().isBlank()
                ? "No extractable text was found in this document."
                : record.getContent().strip());
        textPreview.setCaretPosition(0);

        Path source = record.getSource();
        fileNameLabel.setText(source.getFileName().toString());
        filePathLabel.setText(source.getParent() == null ? source.toString() : source.getParent().toString());
        filePathLabel.setToolTipText(source.toString());
        fileSizeLabel.setText(factHtml("Size", formatBytes(record.getFileSize())));
        pageCountLabel.setText(factHtml("Pages", findPageCount(record.getMetadata())));
        metadataCountLabel.setText(factHtml("Fields", Integer.toString(record.getMetadata().size())));
        previewNoteLabel.setText(record.isContentTruncated()
                ? "Text preview limited to 2,000,000 characters for performance."
                : formatCharacterCount(record.getContent().length()) + " extracted characters");

        resultTabs.setTitleAt(0, "Metadata  " + record.getMetadata().size());
        resultTabs.setSelectedIndex(0);
        exportButton.setEnabled(true);
        setTitle(source.getFileName() + " — " + APP_MAIN_TITLE);
        contentLayout.show(contentCards, CARD_RESULTS);
        setStatus(Status.SUCCESS,
                "Ready · " + record.getMetadata().size() + " metadata fields found");
    }

    private void showWelcome() {
        setBusy(false, "");
        setTitle(APP_MAIN_TITLE);
        contentLayout.show(contentCards, CARD_WELCOME);
        setStatus(Status.NEUTRAL, "Ready · Open or drop a PDF to begin");
    }

    private void exportMetadata() {
        if (documentRecord == null) {
            return;
        }

        Path destination = chooseExportDestination();
        if (destination == null) {
            return;
        }

        destination = withJsonExtension(destination);
        if (Files.exists(destination)) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "A file named \"" + destination.getFileName() + "\" already exists. Replace it?",
                    "Replace existing file?",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try {
            DocumentRepository.export(documentRecord, destination, false);
            rememberDirectory(destination);
            setStatus(Status.SUCCESS, "Exported · " + destination.getFileName());
            offerToReveal(destination);
        } catch (IOException | RuntimeException exception) {
            showError("The JSON file could not be saved. Check the destination and try again.", exception);
        }
    }

    private Path chooseExportDestination() {
        if (isMacOs()) {
            FileDialog chooser = new FileDialog(this, "Export metadata as JSON", FileDialog.SAVE);
            Path sourceDirectory = documentRecord.getSource().getParent();
            if (sourceDirectory != null) {
                chooser.setDirectory(sourceDirectory.toString());
            }
            chooser.setFile(suggestedExportName(documentRecord.getSource()));
            chooser.setFilenameFilter((folder, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
            chooser.setVisible(true);
            return chooser.getFile() == null
                    ? null
                    : Path.of(chooser.getDirectory(), chooser.getFile());
        }

        JFileChooser chooser = new JFileChooser(documentRecord.getSource().getParent().toFile());
        chooser.setDialogTitle("Export metadata as JSON");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("JSON files (*.json)", "json"));
        chooser.setSelectedFile(new File(suggestedExportName(documentRecord.getSource())));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        return chooser.getSelectedFile().toPath();
    }

    private void offerToReveal(Path destination) {
        if (!Desktop.isDesktopSupported() || GraphicsEnvironment.isHeadless()) {
            return;
        }
        statusLabel.setToolTipText("Click to show the exported file");
        statusLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        for (java.awt.event.MouseListener listener : statusLabel.getMouseListeners()) {
            statusLabel.removeMouseListener(listener);
        }
        statusLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                try {
                    Desktop.getDesktop().open(destination.getParent().toFile());
                } catch (IOException ignored) {
                    // Revealing the file is a convenience; the export itself has already succeeded.
                }
            }
        });
    }

    private void copySelectedMetadata() {
        int[] selectedRows = metadataTable.getSelectedRows();
        if (selectedRows.length == 0) {
            return;
        }

        StringBuilder value = new StringBuilder();
        for (int viewRow : selectedRows) {
            Map.Entry<String, String> entry = metadataModel.getEntry(
                    metadataTable.convertRowIndexToModel(viewRow));
            if (!value.isEmpty()) {
                value.append(System.lineSeparator());
            }
            value.append(entry.getKey()).append('\t').append(entry.getValue());
        }

        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new StringSelection(value.toString()), null);
            setStatus(Status.SUCCESS,
                    selectedRows.length == 1 ? "Copied 1 metadata field" : "Copied " + selectedRows.length + " metadata fields");
        } catch (IllegalStateException exception) {
            showError("The system clipboard is temporarily unavailable.", exception);
        }
    }

    private void updateMetadataFilter() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            metadataSorter.setRowFilter(null);
        } else {
            metadataSorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(query)));
        }
    }

    private void toggleTheme() {
        darkMode = !darkMode;
        PREFERENCES.putBoolean(PREF_DARK_MODE, darkMode);
        if (darkMode) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        configureLookAndFeelDefaults();
        SwingUtilities.updateComponentTreeUI(this);
        updateThemeButton();
        refreshThemeColors(getContentPane());
        refreshChromeColors();
        if (!currentStatusMessage.isEmpty()) {
            setStatus(currentStatus, currentStatusMessage);
        }
        if (documentRecord != null) {
            fileSizeLabel.setText(factHtml("Size", formatBytes(documentRecord.getFileSize())));
            pageCountLabel.setText(factHtml("Pages", findPageCount(documentRecord.getMetadata())));
            metadataCountLabel.setText(factHtml("Fields",
                    Integer.toString(documentRecord.getMetadata().size())));
        }
        repaint();
    }

    private void updateThemeButton() {
        if (themeButton != null) {
            themeButton.setText(darkMode ? "Light mode" : "Dark mode");
        }
    }

    private void setBusy(boolean busy, String message) {
        statusProgress.setVisible(busy);
        if (busy) {
            setStatus(Status.PROGRESS, message);
        }
    }

    private void setStatus(Status status, String message) {
        currentStatus = status;
        currentStatusMessage = message;
        Color color = switch (status) {
            case SUCCESS -> darkMode ? new Color(77, 201, 128) : new Color(38, 139, 86);
            case ERROR -> darkMode ? new Color(255, 112, 116) : new Color(201, 52, 59);
            case PROGRESS -> darkMode ? new Color(109, 166, 246) : new Color(55, 112, 190);
            case NEUTRAL -> mutedColor();
        };
        statusLabel.setText("●  " + message);
        statusLabel.setForeground(color);
        if (status != Status.SUCCESS || !message.startsWith("Exported")) {
            statusLabel.setToolTipText(null);
            statusLabel.setCursor(Cursor.getDefaultCursor());
            for (java.awt.event.MouseListener listener : statusLabel.getMouseListeners()) {
                statusLabel.removeMouseListener(listener);
            }
        }
    }

    private void showError(String message, Throwable exception) {
        if (exception != null) {
            org.slf4j.LoggerFactory.getLogger(MetaViewerForm.class)
                    .error(message, exception);
        }
        setBusy(false, "");
        setStatus(Status.ERROR, message);
        JOptionPane.showMessageDialog(
                this,
                "<html><body style='width: 360px'><b>We couldn’t complete that action.</b><br><br>"
                        + escapeHtml(message) + "</body></html>",
                APP_MAIN_TITLE,
                JOptionPane.ERROR_MESSAGE
        );
    }

    private File lastDirectory() {
        String stored = PREFERENCES.get(PREF_LAST_DIRECTORY, "");
        if (!stored.isBlank()) {
            File directory = new File(stored);
            if (directory.isDirectory()) {
                return directory;
            }
        }
        return null;
    }

    private void rememberDirectory(Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        Path directory = Files.isDirectory(absolute) ? absolute : absolute.getParent();
        if (directory != null) {
            PREFERENCES.put(PREF_LAST_DIRECTORY, directory.toString());
        }
    }

    private JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.putClientProperty("FlatLaf.style",
                "arc: 12; background: #CE3E43; hoverBackground: #B83035; pressedBackground: #A42C31; "
                        + "foreground: #FFFFFF; borderWidth: 0; focusWidth: 2; innerFocusWidth: 0");
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));
        button.setPreferredSize(new Dimension(Math.max(112, button.getPreferredSize().width + 28), 38));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JButton secondaryButton(String text) {
        JButton button = new JButton(text);
        button.putClientProperty("FlatLaf.style", "arc: 12; focusWidth: 2; innerFocusWidth: 0");
        button.setPreferredSize(new Dimension(Math.max(104, button.getPreferredSize().width + 24), 38));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JLabel factLabel(String label, String value) {
        JLabel result = new JLabel(factHtml(label, value));
        result.setHorizontalAlignment(SwingConstants.CENTER);
        return result;
    }

    private String factHtml(String label, String value) {
        String muted = darkMode ? "#A8ADB5" : "#6E7580";
        String main = darkMode ? "#F2F3F5" : "#24272C";
        return "<html><div style='text-align:center'><span style='font-size:9px;color:" + muted
                + "'>" + escapeHtml(label.toUpperCase(Locale.ROOT)) + "</span><br>"
                + "<span style='font-size:11px;color:" + main + "'><b>" + escapeHtml(value)
                + "</b></span></div></html>";
    }

    private JLabel mutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty("pdfMeta.muted", true);
        label.setForeground(mutedColor());
        return label;
    }

    private void refreshThemeColors(Component component) {
        if (component instanceof JLabel label
                && Boolean.TRUE.equals(label.getClientProperty("pdfMeta.muted"))) {
            label.setForeground(mutedColor());
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                refreshThemeColors(child);
            }
        }
    }

    private void refreshChromeColors() {
        if (metadataScrollPane != null) {
            metadataScrollPane.setBorder(BorderFactory.createLineBorder(borderColor(), 1, true));
        }
        if (textScrollPane != null) {
            textScrollPane.setBorder(BorderFactory.createLineBorder(borderColor(), 1, true));
        }
        if (statusBar != null) {
            statusBar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, borderColor()),
                    BorderFactory.createEmptyBorder(10, 30, 11, 30)
            ));
        }
    }

    private Color mutedColor() {
        return darkMode ? new Color(166, 171, 180) : new Color(102, 109, 120);
    }

    private Color borderColor() {
        return darkMode ? new Color(67, 71, 78) : new Color(218, 221, 226);
    }

    private static String findPageCount(Map<String, String> metadata) {
        for (String key : List.of("xmpTPg:NPages", "pdf:docinfo:custom:Pages", "Pages")) {
            String value = metadata.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "—";
    }

    private static String suggestedExportName(Path source) {
        String name = source.getFileName().toString();
        int extension = name.toLowerCase(Locale.ROOT).lastIndexOf(".pdf");
        String base = extension > 0 ? name.substring(0, extension) : name;
        return base + "-metadata.json";
    }

    private static Path withJsonExtension(Path path) {
        String fileName = path.getFileName().toString();
        if (fileName.toLowerCase(Locale.ROOT).endsWith(".json")) {
            return path;
        }
        return path.resolveSibling(fileName + ".json");
    }

    private static boolean isMacOs() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("mac");
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        do {
            value /= 1024d;
            unit++;
        } while (value >= 1024 && unit < units.length - 1);
        return new DecimalFormat(value >= 10 ? "0.0" : "0.00").format(value) + " " + units[unit];
    }

    private static String formatCharacterCount(int count) {
        return new DecimalFormat("#,###").format(count);
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static BufferedImage createAppIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(ACCENT);
            graphics.fillRoundRect(size / 8, size / 12, size * 3 / 4, size * 5 / 6,
                    size / 5, size / 5);
            graphics.setColor(new Color(255, 255, 255, 230));
            graphics.fillRoundRect(size * 3 / 10, size * 3 / 10, size * 2 / 5, size / 12,
                    size / 24, size / 24);
            graphics.fillRoundRect(size * 3 / 10, size * 47 / 100, size * 2 / 5, size / 12,
                    size / 24, size / 24);
            graphics.fillRoundRect(size * 3 / 10, size * 64 / 100, size / 4, size / 12,
                    size / 24, size / 24);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private enum Status {
        NEUTRAL,
        PROGRESS,
        SUCCESS,
        ERROR
    }

    private final class RoundedPanel extends JPanel {
        private final int radius;

        private RoundedPanel(int radius) {
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                graphics2D.setColor(darkMode ? new Color(48, 50, 55) : Color.WHITE);
                graphics2D.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
                graphics2D.setColor(borderColor());
                graphics2D.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            } finally {
                graphics2D.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    private static final class AppMark extends JComponent {
        private final int size;

        private AppMark(int size) {
            this.size = size;
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(getPreferredSize());
            setMaximumSize(getPreferredSize());
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Image icon = createAppIcon(size);
            graphics.drawImage(icon, 0, 0, null);
        }
    }

    private final class DocumentIllustration extends JComponent {
        private final int size;

        private DocumentIllustration(int size) {
            this.size = size;
            setPreferredSize(new Dimension(size, size));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                int x = size / 4;
                int y = size / 10;
                int width = size / 2;
                int height = size * 7 / 10;
                graphics2D.setColor(darkMode ? new Color(61, 64, 71) : new Color(246, 247, 249));
                graphics2D.fillRoundRect(x, y, width, height, 14, 14);
                graphics2D.setColor(borderColor());
                graphics2D.setStroke(new BasicStroke(2f));
                graphics2D.drawRoundRect(x, y, width, height, 14, 14);

                graphics2D.setColor(ACCENT);
                graphics2D.fillRoundRect(x + size / 9, y + size / 5, width - size * 2 / 9,
                        7, 4, 4);
                graphics2D.setColor(mutedColor());
                graphics2D.fillRoundRect(x + size / 9, y + size * 17 / 50,
                        width - size * 2 / 9, 6, 4, 4);
                graphics2D.fillRoundRect(x + size / 9, y + size * 24 / 50,
                        width - size * 3 / 10, 6, 4, 4);

                int lensX = size * 59 / 100;
                int lensY = size * 59 / 100;
                graphics2D.setColor(darkMode ? new Color(231, 233, 237) : new Color(48, 52, 58));
                graphics2D.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics2D.drawOval(lensX - 14, lensY - 14, 28, 28);
                graphics2D.drawLine(lensX + 11, lensY + 11, lensX + 26, lensY + 26);
            } finally {
                graphics2D.dispose();
            }
        }
    }

    private final class SearchIcon implements javax.swing.Icon {
        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                graphics2D.setColor(mutedColor());
                graphics2D.setStroke(new BasicStroke(1.6f));
                graphics2D.drawOval(x + 1, y + 1, 9, 9);
                graphics2D.drawLine(x + 9, y + 9, x + 14, y + 14);
            } finally {
                graphics2D.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }
    }

    private final class MetadataCellRenderer extends DefaultTableCellRenderer {
        private MetadataCellRenderer() {
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean selected,
                                                       boolean focused,
                                                       int row,
                                                       int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, value, selected, focused, row, column);
            label.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            if (!selected) {
                label.setBackground(row % 2 == 0
                        ? table.getBackground()
                        : (darkMode ? new Color(45, 47, 52) : new Color(249, 250, 251)));
            }
            label.setFont(label.getFont().deriveFont(column == 0 ? Font.BOLD : Font.PLAIN, 12f));
            label.setToolTipText(value == null ? null : value.toString());
            return label;
        }
    }
}
