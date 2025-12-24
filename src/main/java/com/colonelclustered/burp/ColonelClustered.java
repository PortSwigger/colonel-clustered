
package com.colonelclustered.burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import smile.clustering.PartitionClustering;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ColonelClustered implements BurpExtension, ContextMenuItemsProvider {
    private MontoyaApi api;
    private ColonelClusteredTab colonelClusteredTab;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        api.extension().setName("Colonel Clustered");
        api.logging().logToOutput("Colonel Clustered loaded.");

        colonelClusteredTab = new ColonelClusteredTab(api);
        api.userInterface().registerSuiteTab("Colonel Clustered", colonelClusteredTab);
        api.userInterface().registerContextMenuItemsProvider(this);
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
    private final DefaultTreeModel treeModel;
    private final HttpRequestEditor requestViewer;
    private final HttpResponseEditor responseViewer;
    private final CardLayout cardLayout;
    private final JPanel mainPanel;

    public ColonelClusteredTab(MontoyaApi api) {
        this.api = api;
        this.clusteringEngine = new ClusteringEngine();
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        // --- Status Panel ---
        JPanel statusPanel = new JPanel(new GridBagLayout());
        statusPanel.add(new JLabel("Waiting for analysis... Right-click items and 'Send to Colonel Clustered'."));
        mainPanel.add(statusPanel, "status");

        // --- Results Panel ---
        JPanel resultsPanel = new JPanel(new BorderLayout());
        
        UserInterface userInterface = api.userInterface();
        requestViewer = userInterface.createHttpRequestEditor();
        responseViewer = userInterface.createHttpResponseEditor();
        JSplitPane viewersSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, requestViewer.uiComponent(), responseViewer.uiComponent());
        viewersSplitPane.setResizeWeight(0.5);
        
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("No analysis run yet.");
        treeModel = new DefaultTreeModel(rootNode);
        clusterTree = new JTree(treeModel);
        clusterTree.setCellRenderer(new ClusterTreeCellRenderer());
        clusterTree.addTreeSelectionListener(e -> {
            Object lastPathComponent = e.getPath().getLastPathComponent();
            if (lastPathComponent instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) lastPathComponent;
                Object userObject = selectedNode.getUserObject();
                if (userObject instanceof RequestResponseNode) {
                    HttpRequestResponse selectedRequestResponse = ((RequestResponseNode) userObject).getRequestResponse();
                    requestViewer.setRequest(selectedRequestResponse.request());
                    responseViewer.setResponse(selectedRequestResponse.response());
                }
            }
        });
        JScrollPane treeScrollPane = new JScrollPane(clusterTree);

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScrollPane, viewersSplitPane);
        mainSplitPane.setResizeWeight(0.3);
        resultsPanel.add(mainSplitPane, BorderLayout.CENTER);
        
        mainPanel.add(resultsPanel, "results");

        // --- Top Panel with Buttons ---
        JButton clearButton = new JButton("Clear Results");
        clearButton.addActionListener(e -> clearResults());
        
        JButton aboutButton = new JButton("About");
        aboutButton.addActionListener(e -> showAboutDialog());
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(clearButton);
        buttonPanel.add(aboutButton);

        setLayout(new BorderLayout());
        add(buttonPanel, BorderLayout.NORTH);
        add(mainPanel, BorderLayout.CENTER);
    }

    private void clearResults() {
        treeModel.setRoot(new DefaultMutableTreeNode("Results cleared."));
        requestViewer.setRequest(null);
        responseViewer.setResponse(null);
        cardLayout.show(mainPanel, "status");
    }

    private void showAboutDialog() {
        String htmlContent = "<html><body style='width: 300px; padding: 10px;'>"
            + "<h1>Colonel Clustered</h1>"
            + "<p>A Burp Suite extension for clustering HTTP responses to find outliers.</p>"
            + "<hr>"
            + "<h3>Author</h3>"
            + "<p><b>Drew Kirkpatrick</b><br>"
            + "<b>Twitter:</b> @hoodoer<br>"
            + "<b>Email:</b> hoodoer@bitwisemunitions.dev</p>"
            + "<h3>Consulting</h3>"
            + "<p>Professional security services provided by TrustedSec.</p>"
            + "<p><a href='https://www.trustedsec.com'>www.trustedsec.com</a></p>"
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

        JOptionPane.showMessageDialog(this, editorPane, "About Colonel Clustered", JOptionPane.INFORMATION_MESSAGE);
    }

    public void processRequestResponses(List<HttpRequestResponse> requestResponses) {
        clearResults();
        cardLayout.show(mainPanel, "status");
        JPanel statusPanel = (JPanel) mainPanel.getComponent(0);
        JLabel statusLabel = (JLabel) statusPanel.getComponent(0);
        statusLabel.setText("Starting clustering for " + requestResponses.size() + " items...");

        SwingWorker<Map<Integer, List<HttpRequestResponse>>, String> worker = new SwingWorker<>() {
            @Override
            protected Map<Integer, List<HttpRequestResponse>> doInBackground() {
                return clusteringEngine.clusterResponses(requestResponses, api, this::publish);
            }
            
            @Override
            protected void process(List<String> chunks) {
                statusLabel.setText(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                try {
                    api.logging().logToOutput("Clustering complete. Entering done() method.");
                    Map<Integer, List<HttpRequestResponse>> result = get();
                    api.logging().logToOutput("Result received with " + result.size() + " clusters.");
                    
                    displayResults(result);
                    api.logging().logToOutput("displayResults() method completed.");

                    cardLayout.show(mainPanel, "results");
                    api.logging().logToOutput("Switched to results panel.");

                } catch (InterruptedException | ExecutionException e) {
                    api.logging().logToError(e);
                    api.logging().logToOutput("Error during clustering: " + e.getMessage());
                    statusLabel.setText("Error during clustering: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void displayResults(Map<Integer, List<HttpRequestResponse>> clusteredResponses) {
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Clustering Results (" + clusteredResponses.values().stream().mapToInt(List::size).sum() + " total items)");

        AtomicInteger requestCounter = new AtomicInteger(0);
        clusteredResponses.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                int clusterId = entry.getKey();
                List<HttpRequestResponse> responsesInCluster = entry.getValue();
                String nodeText = (clusterId == PartitionClustering.OUTLIER ? "Outliers" : "Cluster " + clusterId)
                                  + " (" + responsesInCluster.size() + " items)";
                
                DefaultMutableTreeNode clusterNode = new DefaultMutableTreeNode(nodeText);
                
                for (HttpRequestResponse reqResp : responsesInCluster) {
                    // Use the counter for a generic, sequential label
                    RequestResponseNode nodeObject = new RequestResponseNode(reqResp, requestCounter.getAndIncrement());
                    DefaultMutableTreeNode requestNode = new DefaultMutableTreeNode(nodeObject);
                    clusterNode.add(requestNode);
                }
                rootNode.add(clusterNode);
            });
        
        treeModel.setRoot(rootNode);

        // Schedule collapsing on the AWT Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < rootNode.getChildCount(); i++) {
                clusterTree.collapsePath(clusterTree.getPathForRow(i));
            }
        });
    }
}

class RequestResponseNode {
    private final HttpRequestResponse requestResponse;
    private final int requestIndex;

    public RequestResponseNode(HttpRequestResponse requestResponse, int requestIndex) {
        this.requestResponse = requestResponse;
        this.requestIndex = requestIndex;
    }

    public HttpRequestResponse getRequestResponse() {
        return requestResponse;
    }

    @Override
    public String toString() {
        // Return a generic, sequential identifier
        return "Request " + requestIndex;
    }
}

class ClusterTreeCellRenderer extends DefaultTreeCellRenderer {
    private Font defaultFont;
    private Font boldFont;

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

        // Lazy initialization is safer than initializing in the constructor
        if (boldFont == null) {
            defaultFont = getFont();
            if (defaultFont == null) { // Defensive check
                defaultFont = new Font("SansSerif", Font.PLAIN, 12);
            }
            boldFont = new Font(defaultFont.getName(), Font.BOLD, defaultFont.getSize() + 2);
        }

        // Restore default expand/collapse icons
        if (!leaf) {
            if (expanded) {
                setIcon(UIManager.getIcon("Tree.expandedIcon"));
            } else {
                setIcon(UIManager.getIcon("Tree.collapsedIcon"));
            }
        } else {
            setIcon(null);
        }

        // Apply custom styling only to the top-level cluster nodes
        if (value instanceof DefaultMutableTreeNode) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            if (node.getParent() == tree.getModel().getRoot() && node.getChildCount() > 0) {
                setFont(boldFont);
                setBackgroundNonSelectionColor(new Color(235, 235, 255)); // Lighter background
            } else {
                setFont(defaultFont);
                // Use the default L&F color for child nodes
                setBackgroundNonSelectionColor(UIManager.getColor("Tree.background"));
            }
        }
        return this;
    }
}
