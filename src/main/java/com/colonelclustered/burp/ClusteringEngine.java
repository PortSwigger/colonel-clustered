package com.colonelclustered.burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.colonelclustered.burp.tokenizers.Tokenizer;
import com.colonelclustered.burp.tokenizers.TokenizerFactory;

import java.util.*;
import java.util.function.Consumer;

public class ClusteringEngine {

    public static class IndexedHttpRequestResponse {
        private final HttpRequestResponse requestResponse;
        private final int originalIndex;

        public IndexedHttpRequestResponse(HttpRequestResponse requestResponse, int originalIndex) {
            this.requestResponse = requestResponse;
            this.originalIndex = originalIndex;
        }

        public HttpRequestResponse getRequestResponse() { return requestResponse; }
        public int getOriginalIndex() { return originalIndex; }
    }

    private final TokenizerFactory tokenizerFactory;

    public ClusteringEngine() {
        this.tokenizerFactory = new TokenizerFactory();
    }

    public Map<Integer, List<IndexedHttpRequestResponse>> clusterResponses(List<HttpRequestResponse> requestResponses, MontoyaApi api, Consumer<String> progressCallback) {
        if (requestResponses.size() < 2) {
            if (requestResponses.size() == 1) {
                Map<Integer, List<IndexedHttpRequestResponse>> singleCluster = new HashMap<>();
                singleCluster.computeIfAbsent(1, k -> new ArrayList<>()).add(new IndexedHttpRequestResponse(requestResponses.get(0), 0));
                return singleCluster;
            }
            return Collections.emptyMap();
        }

        progressCallback.accept("Step 1/4: Pre-grouping identical responses...");
        Map<Integer, List<Integer>> hashToOriginalIndices = new HashMap<>();
        List<Set<String>> uniqueTokenSets = new ArrayList<>();
        Map<Integer, Integer> hashToUniqueIndex = new HashMap<>();

        for (int i = 0; i < requestResponses.size(); i++) {
            Tokenizer tokenizer = tokenizerFactory.getTokenizer(requestResponses.get(i).response());
            Set<String> tokens = tokenizer.tokenize(requestResponses.get(i).response().body().getBytes());
            int hash = tokens.hashCode();
            hashToOriginalIndices.computeIfAbsent(hash, k -> new ArrayList<>()).add(i);
            if (!hashToUniqueIndex.containsKey(hash)) {
                hashToUniqueIndex.put(hash, uniqueTokenSets.size());
                uniqueTokenSets.add(tokens);
            }
        }
        api.logging().logToOutput("Found " + uniqueTokenSets.size() + " unique response bodies out of " + requestResponses.size() + " total responses.");

        progressCallback.accept("Step 2/4: Calculating similarity matrix...");
        int numUnique = uniqueTokenSets.size();
        double[][] distanceMatrix = new double[numUnique][numUnique];
        for (int i = 0; i < numUnique; i++) {
            for (int j = i; j < numUnique; j++) {
                double dist = calculateJaccardDistance(uniqueTokenSets.get(i), uniqueTokenSets.get(j));
                distanceMatrix[i][j] = dist;
                distanceMatrix[j][i] = dist;
            }
        }
        
        progressCallback.accept("Step 3/4: Determining optimal clustering...");
        List<Set<Integer>> bestClustering = findBestClustering(distanceMatrix, api);

        progressCallback.accept("Step 4/4: Mapping final cluster results...");
        Map<Integer, List<IndexedHttpRequestResponse>> finalClusteredResponses = new HashMap<>();
        List<IndexedHttpRequestResponse> outliers = new ArrayList<>();
        int clusterId = 1;

        bestClustering.sort(Comparator.comparingInt((Set<Integer> s) -> s.size()).reversed());
        
        for (Set<Integer> uniqueCluster : bestClustering) {
            List<IndexedHttpRequestResponse> fullClusterMembers = new ArrayList<>();
            for (int uniqueIndex : uniqueCluster) {
                int targetHash = -1;
                for (Map.Entry<Integer, Integer> entry : hashToUniqueIndex.entrySet()) {
                    if (entry.getValue().equals(uniqueIndex)) {
                        targetHash = entry.getKey();
                        break;
                    }
                }
                if (targetHash != -1) {
                    for (int originalIndex : hashToOriginalIndices.get(targetHash)) {
                        fullClusterMembers.add(new IndexedHttpRequestResponse(requestResponses.get(originalIndex), originalIndex));
                    }
                }
            }

            if (fullClusterMembers.size() == 1 && bestClustering.size() > 1) {
                outliers.addAll(fullClusterMembers);
            } else {
                finalClusteredResponses.put(clusterId++, fullClusterMembers);
            }
        }

        if (!outliers.isEmpty()) {
            finalClusteredResponses.put(-1, outliers);
        }
        
        return finalClusteredResponses;
    }

    private double calculateJaccardDistance(Set<String> set1, Set<String> set2) {
        if (set1.isEmpty() && set2.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        if (union.isEmpty()) return 0.0;
        return 1.0 - ((double) intersection.size() / union.size());
    }
    
    private List<Set<Integer>> findBestClustering(double[][] distanceMatrix, MontoyaApi api) {
        List<Set<Integer>> clusters = new ArrayList<>();
        for (int i = 0; i < distanceMatrix.length; i++) {
            clusters.add(new HashSet<>(Collections.singletonList(i)));
        }

        if (clusters.size() <= 1) return clusters;

        List<Double> mergeDistances = new ArrayList<>();
        // Make a copy to preserve the original N-cluster state
        List<Set<Integer>> originalClusters = new ArrayList<>();
        for (Set<Integer> cluster : clusters) {
            originalClusters.add(new HashSet<>(cluster));
        }

        while (clusters.size() > 1) {
            double minDistance = Double.MAX_VALUE;
            int c1Idx = -1, c2Idx = -1;
            for (int i = 0; i < clusters.size(); i++) {
                for (int j = i + 1; j < clusters.size(); j++) {
                    double currentMinClusterDistance = findMinClusterDistance(clusters.get(i), clusters.get(j), distanceMatrix);
                    if (currentMinClusterDistance < minDistance) {
                        minDistance = currentMinClusterDistance;
                        c1Idx = i;
                        c2Idx = j;
                    }
                }
            }
            if (c1Idx != -1) {
                mergeDistances.add(minDistance);
                Set<Integer> merged = new HashSet<>(clusters.get(c1Idx));
                merged.addAll(clusters.get(c2Idx));
                clusters.remove(c2Idx);
                clusters.remove(c1Idx);
                clusters.add(merged);
            } else {
                break;
            }
        }
        
        double optimalThreshold = 0.5; // Default fallback
        if (!mergeDistances.isEmpty()) {
            double maxJump = 0;
            int bestCutIndex = -1;
            for (int i = 0; i < mergeDistances.size() - 1; i++) {
                double jump = mergeDistances.get(i + 1) - mergeDistances.get(i);
                if (jump > maxJump) {
                    maxJump = jump;
                    bestCutIndex = i;
                }
            }
            optimalThreshold = (bestCutIndex != -1) ? mergeDistances.get(bestCutIndex) + 0.0001 : mergeDistances.get(mergeDistances.size() - 1) + 0.0001;
        }
        api.logging().logToOutput("Automatically determined merge threshold: " + String.format("%.4f", optimalThreshold));
        
        // Sanity Check for needle-in-a-haystack
        if (distanceMatrix.length == 2 && distanceMatrix[0][1] > 0.1) {
             api.logging().logToOutput("Sanity check triggered: Forcing two clusters for high-distance pair.");
             return originalClusters;
        }
        
        clusters.clear();
        for (int i = 0; i < distanceMatrix.length; i++) {
            clusters.add(new HashSet<>(Collections.singletonList(i)));
        }
        
        while (true) {
            double minDistance = Double.MAX_VALUE;
            int c1Idx = -1, c2Idx = -1;
            for (int i = 0; i < clusters.size(); i++) {
                for (int j = i + 1; j < clusters.size(); j++) {
                    double currentMinClusterDistance = findMinClusterDistance(clusters.get(i), clusters.get(j), distanceMatrix);
                    if (currentMinClusterDistance < minDistance) {
                        minDistance = currentMinClusterDistance;
                        c1Idx = i;
                        c2Idx = j;
                    }
                }
            }
            if (minDistance >= optimalThreshold || c1Idx == -1) {
                break;
            }
            Set<Integer> merged = new HashSet<>(clusters.get(c1Idx));
            merged.addAll(clusters.get(c2Idx));
            clusters.remove(c2Idx);
            clusters.remove(c1Idx);
            clusters.add(merged);
        }
        
        return clusters;
    }

    private double findMinClusterDistance(Set<Integer> c1, Set<Integer> c2, double[][] distanceMatrix) {
        double min = Double.MAX_VALUE;
        for (int p1 : c1) {
            for (int p2 : c2) {
                min = Math.min(min, distanceMatrix[p1][p2]);
            }
        }
        return min;
    }
}