
package com.colonelclustered.burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public class ColonelClustered implements BurpExtension, ContextMenuItemsProvider {
    private MontoyaApi api;
    private ColonelClusteredTab colonelClusteredTab;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        api.extension().setName("Colonel Clustered - Response Similarity Clustering");
        api.logging().logToOutput("Colonel Clustered loaded.");

        colonelClusteredTab = new ColonelClusteredTab(api);
        api.userInterface().registerSuiteTab("Col. Clustered", colonelClusteredTab);
        api.userInterface().registerContextMenuItemsProvider(this);
        api.extension().registerUnloadingHandler(() -> {
            colonelClusteredTab.shutdown();
            api.logging().logToOutput("Colonel Clustered unloaded.");
        });
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        List<Component> menuItems = new ArrayList<>();
        List<HttpRequestResponse> selectedMessages = event.selectedRequestResponses();

        if (!selectedMessages.isEmpty()) {
            JMenuItem sendToClusteringMenu = new JMenuItem("Send to Colonel Clustered");
            sendToClusteringMenu.addActionListener(e -> {
                colonelClusteredTab.processRequestResponses(selectedMessages);
            });
            menuItems.add(sendToClusteringMenu);
        }
        return menuItems;
    }
}

class ColonelClusteredTab extends JPanel {
    private final MontoyaApi api;
    private final ClusteringEngine clusteringEngine;
    private final JTree clusterTree;
    private final JTable requestTable;
    private final RequestResponseTableModel tableModel;
    private final DefaultTreeModel treeModel;
    private final HttpRequestEditor requestViewer;
    private final HttpResponseEditor responseViewer;
    private final CardLayout cardLayout;
    private final JPanel mainPanel;
    private final JLabel resultsLabel;
    private Map<DefaultMutableTreeNode, List<RequestResponseNode>> clusterNodeData;
    private final JButton deepAnalysisButton;
    private List<HttpRequestResponse> currentRequestResponses;
    private final JProgressBar progressBar;
    private final JLabel progressStatusLabel;
    private SwingWorker<?, ?> currentWorker;
    private volatile boolean shuttingDown = false;


    public ColonelClusteredTab(MontoyaApi api) {
        this.api = api;
        this.clusteringEngine = new ClusteringEngine(api);
        this.clusterNodeData = new HashMap<>();
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        // --- Status Panel ---
        JPanel statusPanel = new JPanel(new GridBagLayout());
        statusPanel.add(new JLabel("Waiting for analysis... Right-click items and 'Send to Colonel Clustered'."));
        mainPanel.add(statusPanel, "status");

        // --- Generic Progress Panel ---
        JPanel progressPanel = new JPanel(new GridBagLayout());
        progressPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel container = new JPanel(new BorderLayout(10, 10));

        progressStatusLabel = new JLabel("Starting analysis...", SwingConstants.CENTER);
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        
        JPanel progressCenterPanel = new JPanel();
        progressCenterPanel.setLayout(new BoxLayout(progressCenterPanel, BoxLayout.Y_AXIS));
        progressCenterPanel.add(progressStatusLabel);
        progressCenterPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        progressCenterPanel.add(progressBar);
        
        container.add(progressCenterPanel, BorderLayout.CENTER);

        JButton cancelAnalysisButton = new JButton("Cancel");
        cancelAnalysisButton.addActionListener(e -> {
            clusteringEngine.cancel();
        });
        container.add(cancelAnalysisButton, BorderLayout.SOUTH);

        progressPanel.add(container, new GridBagConstraints());
        mainPanel.add(progressPanel, "progress");

        // --- Results Panel ---
        JPanel resultsPanel = new JPanel(new BorderLayout());

        UserInterface userInterface = api.userInterface();
        requestViewer = userInterface.createHttpRequestEditor();
        responseViewer = userInterface.createHttpResponseEditor();
        JSplitPane viewersSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, requestViewer.uiComponent(), responseViewer.uiComponent());
        viewersSplitPane.setResizeWeight(0.5);

        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Root");
        treeModel = new DefaultTreeModel(rootNode);
        clusterTree = new JTree(treeModel);
        clusterTree.setRootVisible(false);
        clusterTree.setCellRenderer(new ClusterTreeCellRenderer());

        tableModel = new RequestResponseTableModel();
        requestTable = new JTable(tableModel);
        requestTable.setAutoCreateRowSorter(true);

        DefaultTableCellRenderer leftRenderer = new DefaultTableCellRenderer();
        leftRenderer.setHorizontalAlignment(SwingConstants.LEFT);
        for (int i = 0; i < requestTable.getColumnCount(); i++) {
            requestTable.getColumnModel().getColumn(i).setCellRenderer(leftRenderer);
        }
        DefaultTableCellRenderer headerRenderer = (DefaultTableCellRenderer) requestTable.getTableHeader().getDefaultRenderer();
        headerRenderer.setHorizontalAlignment(SwingConstants.LEFT);

        clusterTree.addTreeSelectionListener(e -> {
            Object lastPathComponent = e.getPath().getLastPathComponent();
            if (lastPathComponent instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) lastPathComponent;
                if (clusterNodeData.containsKey(selectedNode)) {
                    tableModel.setRequestResponses(clusterNodeData.get(selectedNode));
                }
            }
        });

        requestTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = requestTable.getSelectedRow();
                if (selectedRow != -1) {
                    int modelRow = requestTable.convertRowIndexToModel(selectedRow);
                    RequestResponseNode selectedNode = tableModel.getRequestResponseAt(modelRow);
                    if (selectedNode != null) {
                        requestViewer.setRequest(selectedNode.getRequestResponse().request());
                        responseViewer.setResponse(selectedNode.getRequestResponse().response());
                    }
                }
            }
        });

        JScrollPane treeScrollPane = new JScrollPane(clusterTree);
        JScrollPane tableScrollPane = new JScrollPane(requestTable);

        JSplitPane leftSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, treeScrollPane, tableScrollPane);
        leftSplitPane.setResizeWeight(0.3);

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplitPane, viewersSplitPane);
        mainSplitPane.setResizeWeight(0.3);

        resultsLabel = new JLabel("No analysis run yet.", SwingConstants.CENTER);
        resultsLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        resultsPanel.add(resultsLabel, BorderLayout.NORTH);
        resultsPanel.add(mainSplitPane, BorderLayout.CENTER);

        mainPanel.add(resultsPanel, "results");

        // --- Top Panel with Buttons ---
        JButton clearButton = new JButton("Clear Results");
        clearButton.addActionListener(e -> clearResults());

        deepAnalysisButton = new JButton("Deep Analysis");
        deepAnalysisButton.setEnabled(false);
        deepAnalysisButton.addActionListener(e -> performDeepAnalysis());

        JButton aboutButton = new JButton("About");
        aboutButton.addActionListener(e -> showAboutDialog());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(clearButton);
        buttonPanel.add(deepAnalysisButton);
        buttonPanel.add(aboutButton);

        setLayout(new BorderLayout());
        add(buttonPanel, BorderLayout.NORTH);
        add(mainPanel, BorderLayout.CENTER);
    }

    void shutdown() {
        shuttingDown = true;
        clusteringEngine.cancel();
        if (currentWorker != null) {
            currentWorker.cancel(true);
            currentWorker = null;
        }
        currentRequestResponses = null;
        clusterNodeData = new HashMap<>(); // release held data without breaking listeners that may still fire during teardown
    }

    private void clearResults() {
        treeModel.setRoot(new DefaultMutableTreeNode("Root"));
        tableModel.setRequestResponses(new ArrayList<>()); // Clear table
        resultsLabel.setText("Results cleared.");
        requestViewer.setRequest(null);
        responseViewer.setResponse(null);
        deepAnalysisButton.setEnabled(false);
        currentRequestResponses = null;
        cardLayout.show(mainPanel, "status");
    }

    private void showAboutDialog() {
        // ... (existing code, no changes)
        String version = getClass().getPackage().getImplementationVersion();
        if (version == null) {
            version = "DEV";
        }
        String htmlContent = "<html><body style='width: 300px; padding: 10px;'>"
            + "<h1>Colonel Clustered</h1>"
            + "<p><b>Version:</b> " + version + "</p>"
            + "<p>A Burp Suite extension for clustering HTTP responses to find outliers.</p>"
            + "<hr>"
            + "<h3>Author</h3>"
            + "<p><b>Drew Kirkpatrick</b><br>"
            + "<b>Twitter:</b> @hoodoer<br>"
            + "<b>Email:</b> hoodoer@bitwisemunitions.dev</p>"
            + "</body></html>";
        JEditorPane editorPane = new JEditorPane("text/html", htmlContent);
        editorPane.setEditable(false);
        editorPane.setOpaque(false);
        editorPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                try {
                    Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (IOException | URISyntaxException ex) {
                    api.logging().logToError(ex);
                }
            }
        });
        Frame suiteFrame = api.userInterface().swingUtils().suiteFrame();
        JOptionPane.showMessageDialog(suiteFrame, editorPane, "About Colonel Clustered", JOptionPane.INFORMATION_MESSAGE);
    }
    
    public void processRequestResponses(List<HttpRequestResponse> requestResponses) {
        clearResults();

        // Items from proxy history may have no response (e.g. aborted or in-flight requests)
        List<HttpRequestResponse> analyzable = new ArrayList<>();
        for (HttpRequestResponse requestResponse : requestResponses) {
            if (requestResponse.response() != null) {
                analyzable.add(requestResponse);
            }
        }
        int skipped = requestResponses.size() - analyzable.size();
        if (skipped > 0) {
            api.logging().logToOutput("Skipped " + skipped + " item(s) with no response.");
        }
        if (analyzable.isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel,
                "None of the selected items have a response to analyze.",
                "Nothing to Analyze", JOptionPane.WARNING_MESSAGE);
            return;
        }

        this.currentRequestResponses = analyzable;
        cardLayout.show(mainPanel, "progress");
        progressBar.setValue(0);
        progressStatusLabel.setText("Starting fast clustering for " + analyzable.size() + " items...");

        SwingWorker<Map<Integer, List<ClusteringEngine.IndexedHttpRequestResponse>>, String> worker = new SwingWorker<>() {
            @Override
            protected Map<Integer, List<ClusteringEngine.IndexedHttpRequestResponse>> doInBackground() {
                return clusteringEngine.clusterResponses(analyzable, this::publish);
            }

            @Override
            protected void process(List<String> chunks) {
                if (chunks.isEmpty()) return;
                String latestStatus = chunks.get(chunks.size() - 1);
                
                String[] parts = latestStatus.split("\\|");
                if (parts.length == 2) {
                    String message = parts[0];
                    try {
                        int percent = Integer.parseInt(parts[1]);
                        progressStatusLabel.setText(message);
                        progressBar.setValue(percent);
                        progressBar.setString(percent + "%");
                    } catch (NumberFormatException e) {
                        progressStatusLabel.setText(message);
                    }
                } else {
                    progressStatusLabel.setText(latestStatus);
                }
            }

            @Override
            protected void done() {
                if (shuttingDown) {
                    return; // extension is unloading; skip API and UI updates
                }
                try {
                    Map<Integer, List<ClusteringEngine.IndexedHttpRequestResponse>> result = get();
                    displayResults(result);
                    deepAnalysisButton.setEnabled(true);
                } catch (InterruptedException | CancellationException e) {
                    api.logging().logToOutput("Fast clustering cancelled.");
                    resultsLabel.setText("Fast clustering cancelled.");
                } catch (ExecutionException e) {
                    api.logging().logToError(e.getCause());
                    resultsLabel.setText("Error during clustering: " + e.getCause().getMessage());
                    JOptionPane.showMessageDialog(mainPanel,
                        "An error occurred during fast analysis: " + e.getCause().getMessage(),
                        "Analysis Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    cardLayout.show(mainPanel, "results");
                }
            }
        };
        this.currentWorker = worker;
        clusteringEngine.start();
        worker.execute();
    }

    private void performDeepAnalysis() {
        if (currentRequestResponses == null || currentRequestResponses.isEmpty()) {
            return;
        }

        int requestCount = currentRequestResponses.size();
        if (requestCount > 2000) { // Example threshold
            int choice = JOptionPane.showConfirmDialog(
                this,
                "You are about to run a deep analysis on " + requestCount + " items.\n" +
                "This can be very slow and memory-intensive for a large number of items.\n\n" +
                "Are you sure you want to continue?",
                "Performance Warning",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );

            if (choice == JOptionPane.NO_OPTION) {
                return; // User cancelled
            }
        }

        deepAnalysisButton.setEnabled(false);
        cardLayout.show(mainPanel, "progress");
        
        progressBar.setValue(0);
        progressStatusLabel.setText("Starting deep hierarchical analysis...");

        SwingWorker<Map<Integer, List<ClusteringEngine.IndexedHttpRequestResponse>>, String> deepAnalysisWorker = new SwingWorker<>() {
            @Override
            protected Map<Integer, List<ClusteringEngine.IndexedHttpRequestResponse>> doInBackground() throws Exception {
                return clusteringEngine.runDeepClustering(currentRequestResponses, this::publish);
            }

            @Override
            protected void process(List<String> chunks) {
                if (chunks.isEmpty()) return;
                String latestStatus = chunks.get(chunks.size() - 1);
                
                String[] parts = latestStatus.split("\\|");
                if (parts.length == 2) {
                    String message = parts[0];
                    try {
                        int percent = Integer.parseInt(parts[1]);
                        progressStatusLabel.setText(message);
                        progressBar.setValue(percent);
                        progressBar.setString(percent + "%");
                    } catch (NumberFormatException e) {
                        progressStatusLabel.setText(message);
                    }
                } else {
                    progressStatusLabel.setText(latestStatus);
                }
            }

            @Override
            protected void done() {
                if (shuttingDown) {
                    return; // extension is unloading; skip API and UI updates
                }
                try {
                    if (!isCancelled()) {
                        Map<Integer, List<ClusteringEngine.IndexedHttpRequestResponse>> result = get();
                        displayResults(result);
                        api.logging().logToOutput("Deep analysis complete.");
                    } else {
                        api.logging().logToOutput("Deep analysis was cancelled by the user.");
                        resultsLabel.setText(resultsLabel.getText() + " (Deep analysis cancelled)");
                    }
                } catch (InterruptedException | CancellationException e) {
                    api.logging().logToOutput("Deep analysis was cancelled by the user.");
                    resultsLabel.setText(resultsLabel.getText() + " (Deep analysis cancelled)");
                    JOptionPane.showMessageDialog(mainPanel,
                        e.getMessage(),
                        "Analysis Cancelled", JOptionPane.WARNING_MESSAGE);
                } catch (ExecutionException e) {
                    api.logging().logToError(e.getCause());
                    JOptionPane.showMessageDialog(mainPanel,
                        "An error occurred during deep analysis: " + e.getCause().getMessage(),
                        "Analysis Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    cardLayout.show(mainPanel, "results");
                    deepAnalysisButton.setEnabled(true);
                }
            }
        };
        this.currentWorker = deepAnalysisWorker;
        clusteringEngine.start();
        deepAnalysisWorker.execute();
    }


    private void displayResults(Map<Integer, List<ClusteringEngine.IndexedHttpRequestResponse>> clusteredResponses) {
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Root");
        clusterNodeData.clear();

        long totalItems = clusteredResponses.values().stream().mapToLong(List::size).sum();
        resultsLabel.setText("Clustering Results (" + totalItems + " total items)");

        clusteredResponses.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                int clusterId = entry.getKey();
                List<ClusteringEngine.IndexedHttpRequestResponse> responsesInCluster = entry.getValue();
                String nodeText = (clusterId == -1 ? "Outliers" : "Cluster " + clusterId)
                                  + " (" + responsesInCluster.size() + " items)";

                DefaultMutableTreeNode clusterNode = new DefaultMutableTreeNode(nodeText);

                List<RequestResponseNode> nodeObjects = new ArrayList<>();
                for (ClusteringEngine.IndexedHttpRequestResponse indexedReqResp : responsesInCluster) {
                    nodeObjects.add(new RequestResponseNode(indexedReqResp.getRequestResponse(), indexedReqResp.getOriginalIndex()));
                }
                clusterNodeData.put(clusterNode, nodeObjects);

                rootNode.add(clusterNode);
            });

        treeModel.setRoot(rootNode);
    }
}

class RequestResponseTableModel extends AbstractTableModel {
    // ... (existing code, no changes)
        private final String[] columnNames = {"Request/Response Pair", "Status Code", "Length", "Content-Type"};
        private List<RequestResponseNode> requestResponses = new ArrayList<>();
    
        @Override
        public int getRowCount() {
            return requestResponses.size();
        }
    
        @Override
        public int getColumnCount() {
            return columnNames.length;
        }
    
        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }
    
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            switch (columnIndex) {
                case 0: // Request/Response Pair
                case 1: // Status Code
                case 2: // Length
                    return Integer.class;
                case 3: // Content-Type
                    return String.class;
                default:
                    return Object.class;
            }
        }
    
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            RequestResponseNode node = requestResponses.get(rowIndex);
            HttpRequestResponse reqResp = node.getRequestResponse();
    
            switch (columnIndex) {
                case 0:
                    return node.getOriginalIndex();
                case 1:
                    return (int) reqResp.response().statusCode();
                case 2:
                    return reqResp.response().body().length();
                case 3:
                    // Extract Content-Type header
                    return reqResp.response().headers().stream()
                            .filter(h -> h.name().equalsIgnoreCase("Content-Type"))
                            .map(h -> h.value())
                            .findFirst()
                            .orElse("N/A");
                default:
                    return null;
            }
        }
    
        public void setRequestResponses(List<RequestResponseNode> requestResponses) {
            this.requestResponses = new ArrayList<>(requestResponses);
            fireTableDataChanged();
        }
    
        public RequestResponseNode getRequestResponseAt(int rowIndex) {
            if (rowIndex >= 0 && rowIndex < requestResponses.size()) {
                return requestResponses.get(rowIndex);
            }
            return null;
        }
}

class RequestResponseNode {
    // ... (existing code, no changes)
        private final HttpRequestResponse requestResponse;
        private final int originalIndex;
    
        public RequestResponseNode(HttpRequestResponse requestResponse, int originalIndex) {
            this.requestResponse = requestResponse;
            this.originalIndex = originalIndex;
        }
    
        public HttpRequestResponse getRequestResponse() {
            return requestResponse;
        }
    
                public int getOriginalIndex() {
                    return originalIndex;
                }
}

class ClusterTreeCellRenderer extends DefaultTreeCellRenderer {
    // ... (existing code, no changes)
                private Font boldFont;
            
                @Override
                public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                    super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            
                    if (boldFont == null) {
                        // Derive a new font that is larger and bold
                        boldFont = getFont().deriveFont(Font.BOLD, getFont().getSize() + 2f);
                    }
                    setFont(boldFont);
                    
                    return this;
                }
}
