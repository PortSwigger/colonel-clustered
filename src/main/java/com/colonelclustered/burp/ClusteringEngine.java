package com.colonelclustered.burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.colonelclustered.burp.tokenizers.Tokenizer;
import com.colonelclustered.burp.tokenizers.TokenizerFactory;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.distance.DistanceMeasure;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ClusteringEngine {

    private static class TokenSetPoint implements Clusterable {
        private final int id;
        private final Set<Integer> tokenSet;
        private final int tokenSetHash;

        public TokenSetPoint(int id, Set<Integer> tokenSet) {
            this.id = id;
            this.tokenSet = tokenSet;
            this.tokenSetHash = tokenSet.hashCode();
        }

        public int getId() { return id; }
        public Set<Integer> getTokenSet() { return tokenSet; }
        public int getTokenSetHash() { return tokenSetHash; }
        @Override public double[] getPoint() { return new double[0]; }
    }

    private class JaccardDistance implements DistanceMeasure {
        @Override public double compute(double[] a, double[] b) { return 0; }
        public double distance(TokenSetPoint p1, TokenSetPoint p2) {
            return calculateJaccardDistance(p1.getTokenSet(), p2.getTokenSet());
        }
    }

    public static class IndexedHttpRequestResponse {
        private final HttpRequestResponse requestResponse;
        private final int originalIndex;
        public IndexedHttpRequestResponse(HttpRequestResponse r, int i) { this.requestResponse = r; this.originalIndex = i; }
        public HttpRequestResponse getRequestResponse() { return requestResponse; }
        public int getOriginalIndex() { return originalIndex; }
    }

    private final TokenizerFactory tokenizerFactory;
    private final MontoyaApi api;

    public ClusteringEngine(MontoyaApi api) {
        this.api = api;
        this.tokenizerFactory = new TokenizerFactory(api);
    }
    
    public Map<Integer, List<IndexedHttpRequestResponse>> clusterResponses(List<HttpRequestResponse> requestResponses, Consumer<String> progressCallback) {
        return runFastClustering(requestResponses, progressCallback);
    }

    public Map<Integer, List<IndexedHttpRequestResponse>> runFastClustering(List<HttpRequestResponse> requestResponses, Consumer<String> progressCallback) {
        if (requestResponses.size() < 2) { return Collections.emptyMap(); }

        progressCallback.accept("Step 1/3: Pre-processing...");
        PreprocessedData data = preprocessResponses(requestResponses);
        api.logging().logToOutput("Found " + data.uniqueTokenSets.size() + " unique response bodies.");

        if (data.uniqueTokenSets.size() < 2) {
            return Collections.singletonMap(1, IntStream.range(0, requestResponses.size())
                .mapToObj(i -> new IndexedHttpRequestResponse(requestResponses.get(i), i))
                .collect(Collectors.toList()));
        }
        
        progressCallback.accept("Step 2/3: Auto-tuning...");
        List<TokenSetPoint> points = IntStream.range(0, data.uniqueTokenSets.size())
                .mapToObj(i -> new TokenSetPoint(i, data.uniqueTokenSets.get(i)))
                .collect(Collectors.toList());
        
        int minPts = 2;
        List<Double> kDistances = calculateKDistances(points, minPts);
        double epsilon = estimateEpsilonFromElbow(kDistances, api);
        
        DbscanResult result = dbscan(points, epsilon, minPts, new JaccardDistance());
        
        api.logging().logToOutput("DBSCAN finished with " + result.clusters.size() + " clusters.");

        progressCallback.accept("Step 3/3: Mapping results...");
        return mapResults(result, data, requestResponses);
    }

    private static class DbscanResult {
        final List<List<TokenSetPoint>> clusters;
        final List<TokenSetPoint> noise;
        DbscanResult(List<List<TokenSetPoint>> clusters, List<TokenSetPoint> noise) {
            this.clusters = clusters;
            this.noise = noise;
        }
    }

    private DbscanResult dbscan(List<TokenSetPoint> points, double epsilon, int minPts, JaccardDistance dist) {
        List<List<TokenSetPoint>> clusters = new ArrayList<>();
        Map<TokenSetPoint, PointStatus> statusMap = new HashMap<>();

        for (TokenSetPoint point : points) {
            if (statusMap.get(point) == PointStatus.VISITED) {
                continue;
            }
            statusMap.put(point, PointStatus.VISITED);
            List<TokenSetPoint> neighbors = regionQuery(point, points, epsilon, dist);
            // A point is a core point if it has at least minPts - 1 neighbors (since the point itself is not in the list)
            if (neighbors.size() < minPts - 1) {
                statusMap.put(point, PointStatus.NOISE);
            } else {
                List<TokenSetPoint> newCluster = new ArrayList<>();
                expandCluster(point, neighbors, newCluster, points, epsilon, minPts, dist, statusMap);
                clusters.add(newCluster);
            }
        }
        
        List<TokenSetPoint> noisePoints = statusMap.entrySet().stream()
                .filter(e -> e.getValue() == PointStatus.NOISE)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return new DbscanResult(clusters, noisePoints);
    }

    private void expandCluster(TokenSetPoint point, List<TokenSetPoint> neighbors, List<TokenSetPoint> cluster, List<TokenSetPoint> points, double epsilon, int minPts, JaccardDistance dist, Map<TokenSetPoint, PointStatus> statusMap) {
        cluster.add(point);
        statusMap.put(point, PointStatus.PART_OF_CLUSTER);

        for (int i = 0; i < neighbors.size(); i++) {
            TokenSetPoint currentNeighbor = neighbors.get(i);
            PointStatus pStatus = statusMap.get(currentNeighbor);

            if (pStatus == null) {
                statusMap.put(currentNeighbor, PointStatus.VISITED);
                List<TokenSetPoint> newNeighbors = regionQuery(currentNeighbor, points, epsilon, dist);
                if (newNeighbors.size() >= minPts) {
                    neighbors.addAll(newNeighbors);
                }
            }

            if (pStatus != PointStatus.PART_OF_CLUSTER) {
                statusMap.put(currentNeighbor, PointStatus.PART_OF_CLUSTER);
                cluster.add(currentNeighbor);
            }
        }
    }

    private List<TokenSetPoint> regionQuery(TokenSetPoint p, List<TokenSetPoint> points, double epsilon, JaccardDistance dist) {
        return points.stream().filter(other -> p != other && dist.distance(p, other) <= epsilon).collect(Collectors.toList());
    }

    private enum PointStatus { VISITED, NOISE, PART_OF_CLUSTER }

    private PreprocessedData preprocessResponses(List<HttpRequestResponse> requestResponses) {
        Set<String> vocabulary = requestResponses.parallelStream().flatMap(reqResp -> tokenizerFactory.getTokenizer(reqResp.response()).tokenize(reqResp.response().body().getBytes()).stream()).collect(Collectors.toSet());
        final Map<String, Integer> vocabMap = new HashMap<>();
        int id = 0;
        for (String token : vocabulary) { vocabMap.put(token, id++); }
        final ConcurrentHashMap<Integer, List<Integer>> hashToOriginalIndices = new ConcurrentHashMap<>();
        final ConcurrentHashMap<Integer, Set<Integer>> hashToUniqueTokenSet = new ConcurrentHashMap<>();
        IntStream.range(0, requestResponses.size()).parallel().forEach(originalIndex -> {
            HttpRequestResponse requestResponse = requestResponses.get(originalIndex);
            Set<String> stringTokens = tokenizerFactory.getTokenizer(requestResponse.response()).tokenize(requestResponse.response().body().getBytes());
            Set<Integer> intTokens = stringTokens.stream().map(vocabMap::get).collect(Collectors.toSet());
            int hash = intTokens.hashCode();
            hashToUniqueTokenSet.putIfAbsent(hash, intTokens);
            hashToOriginalIndices.computeIfAbsent(hash, k -> new CopyOnWriteArrayList<>()).add(originalIndex);
        });
        List<Set<Integer>> uniqueTokenSets = new ArrayList<>(hashToUniqueTokenSet.values());
        Map<Integer, Integer> hashToUniqueIndex = new HashMap<>();
        for (int i = 0; i < uniqueTokenSets.size(); i++) {
            hashToUniqueIndex.put(uniqueTokenSets.get(i).hashCode(), i);
        }
        return new PreprocessedData(uniqueTokenSets, hashToOriginalIndices, hashToUniqueIndex);
    }
    
    private static class PreprocessedData {
        final List<Set<Integer>> uniqueTokenSets;
        final ConcurrentHashMap<Integer, List<Integer>> hashToOriginalIndices;
        final Map<Integer, Integer> hashToUniqueIndex;
        PreprocessedData(List<Set<Integer>> u, ConcurrentHashMap<Integer, List<Integer>> h1, Map<Integer, Integer> h2) {
            this.uniqueTokenSets = u; this.hashToOriginalIndices = h1; this.hashToUniqueIndex = h2;
        }
    }

    private List<Double> calculateKDistances(List<TokenSetPoint> points, int minPts) {
        List<TokenSetPoint> sample = points;
        if (points.size() > 500) {
            Collections.shuffle(sample, new Random(0));
            sample = sample.subList(0, 500);
        }
        JaccardDistance jaccard = new JaccardDistance();
        List<Double> kDistances = new ArrayList<>();
        for (TokenSetPoint p1 : sample) {
            List<Double> distances = new ArrayList<>();
            for (TokenSetPoint p2 : sample) if (p1 != p2) distances.add(jaccard.distance(p1, p2));
            Collections.sort(distances);
            if (distances.size() >= minPts - 1) kDistances.add(distances.get(minPts - 2));
        }
        Collections.sort(kDistances);
        return kDistances;
    }

    private double estimateEpsilonFromElbow(List<Double> kDistances, MontoyaApi api) {
        if (kDistances.isEmpty()) {
            return 0.5;
        }

        api.logging().logToOutput("Sorted k-distances: " + kDistances.stream().map(d -> String.format("%.4f", d)).collect(Collectors.joining(", ")));
        
        int elbowIndex = Kneedle.findElbowIndex(kDistances);

        double epsilon;
        if (elbowIndex != -1) {
            epsilon = kDistances.get(elbowIndex);
        } else {
            // Fallback if no elbow is found (e.g., list is too small or linear)
            epsilon = kDistances.get(kDistances.size() / 2); // A reasonable guess
        }

        api.logging().logToOutput("Automatically determined epsilon: " + String.format("%.4f", epsilon));
        return Math.max(epsilon, 0.001); // Ensure epsilon is not zero
    }
    
    private Map<Integer, List<IndexedHttpRequestResponse>> mapResults(DbscanResult result, PreprocessedData data, List<HttpRequestResponse> requestResponses) {
        Map<Integer, List<IndexedHttpRequestResponse>> finalClusteredResponses = new HashMap<>();
        List<IndexedHttpRequestResponse> outliers = new ArrayList<>();
        int clusterId = 1;

        result.clusters.sort(Comparator.comparingInt((List<TokenSetPoint> c) -> c.size()).reversed());

        for (List<TokenSetPoint> cluster : result.clusters) {
            List<IndexedHttpRequestResponse> fullClusterMembers = new ArrayList<>();
            for (TokenSetPoint point : cluster) {
                int targetHash = point.getTokenSetHash();
                List<Integer> originalIndices = data.hashToOriginalIndices.get(targetHash);
                if (originalIndices != null) {
                    for (int originalIndex : originalIndices) {
                        fullClusterMembers.add(new IndexedHttpRequestResponse(requestResponses.get(originalIndex), originalIndex));
                    }
                }
            }
            finalClusteredResponses.put(clusterId++, fullClusterMembers);
        }
        
        for (TokenSetPoint point : result.noise) {
            int targetHash = point.getTokenSetHash();
            List<Integer> originalIndices = data.hashToOriginalIndices.get(targetHash);
            if (originalIndices != null) {
                for (int originalIndex : originalIndices) {
                    outliers.add(new IndexedHttpRequestResponse(requestResponses.get(originalIndex), originalIndex));
                }
            }
        }

        if (!outliers.isEmpty()) {
            finalClusteredResponses.put(-1, outliers);
        }
        return finalClusteredResponses;
    }

    public Map<Integer, List<IndexedHttpRequestResponse>> runDeepClustering(List<HttpRequestResponse> requestResponses, Consumer<String> progressCallback) throws InterruptedException {
        if (requestResponses.size() < 2) return Collections.emptyMap();
        progressCallback.accept("Preprocessing...|5");
        PreprocessedData data = preprocessResponses(requestResponses);
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        api.logging().logToOutput("Found " + data.uniqueTokenSets.size() + " unique response bodies.");
        int numUnique = data.uniqueTokenSets.size();
        double[][] distanceMatrix = new double[numUnique][numUnique];
        long totalDistanceCalcs = Math.max(1, (long)numUnique * (numUnique - 1) / 2);
        long completedDistanceCalcs = 0;
        for (int i = 0; i < numUnique; i++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            for (int j = i + 1; j < numUnique; j++) {
                double dist = calculateJaccardDistance(data.uniqueTokenSets.get(i), data.uniqueTokenSets.get(j));
                distanceMatrix[i][j] = dist; distanceMatrix[j][i] = dist;
                completedDistanceCalcs++;
            }
            int percent = 5 + (int) (30.0 * completedDistanceCalcs / totalDistanceCalcs);
            progressCallback.accept("Calculating distance matrix...|" + percent);
        }
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        List<Set<Integer>> bestClustering = findBestClustering(distanceMatrix, (status, percent) -> {
            progressCallback.accept(status + "|" + percent);
        });
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        progressCallback.accept("Mapping results...|99");
        Map<Integer, List<IndexedHttpRequestResponse>> finalClusteredResponses = new HashMap<>();
        List<IndexedHttpRequestResponse> outliers = new ArrayList<>();
        int clusterId = 1;
        bestClustering.sort(Comparator.comparingInt((Set<Integer> s) -> s.size()).reversed());
        for (Set<Integer> uniqueCluster : bestClustering) {
            List<IndexedHttpRequestResponse> fullClusterMembers = new ArrayList<>();
            for (int uniqueIndex : uniqueCluster) {
                int targetHash = data.hashToUniqueIndex.entrySet().stream().filter(e -> e.getValue().equals(uniqueIndex)).map(Map.Entry::getKey).findFirst().orElse(-1);
                if (targetHash != -1) {
                    for (int originalIndex : data.hashToOriginalIndices.get(targetHash)) {
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

    private double calculateJaccardDistance(Set<Integer> set1, Set<Integer> set2) {
        if (set1.isEmpty() && set2.isEmpty()) return 0.0;
        Set<Integer> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        Set<Integer> union = new HashSet<>(set1);
        union.addAll(set2);
        if (union.isEmpty()) return 0.0;
        return 1.0 - ((double) intersection.size() / union.size());
    }
    
    private List<Set<Integer>> findBestClustering(double[][] distanceMatrix, BiConsumer<String, Integer> progressCallback) throws InterruptedException {
        List<Set<Integer>> clusters = new ArrayList<>();
        for (int i = 0; i < distanceMatrix.length; i++) {
            clusters.add(new HashSet<>(Collections.singletonList(i)));
        }
        if (clusters.size() <= 1) return clusters;

        List<Set<Integer>> originalClusters = clusters.stream().map(HashSet::new).collect(Collectors.toList());
        List<Double> mergeDistances = new ArrayList<>();
        List<List<Set<Integer>>> clusterHistory = new ArrayList<>();

        int totalMerges = clusters.size() - 1;
        int mergesCompleted = 0;
        while (clusters.size() > 1) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            double minDistance = Double.MAX_VALUE;
            int c1Idx = -1, c2Idx = -1;
            for (int i = 0; i < clusters.size(); i++) {
                for (int j = i + 1; j < clusters.size(); j++) {
                    double currentMinClusterDistance = findAverageClusterDistance(clusters.get(i), clusters.get(j), distanceMatrix);
                    if (currentMinClusterDistance < minDistance) {
                        minDistance = currentMinClusterDistance; c1Idx = i; c2Idx = j;
                    }
                }
            }
            if (c1Idx != -1) {
                mergeDistances.add(minDistance);
                Set<Integer> merged = new HashSet<>(clusters.get(c1Idx));
                merged.addAll(clusters.get(c2Idx));
                clusters.remove(c2Idx); clusters.remove(c1Idx);
                clusters.add(merged);

                clusterHistory.add(clusters.stream().map(HashSet::new).collect(Collectors.toList()));

                mergesCompleted++;
                int percent = 35 + (int)(60.0 * mergesCompleted / totalMerges);
                progressCallback.accept("Clustering...", percent);
            } else {
                break;
            }
        }

        if (mergeDistances.isEmpty()) {
            return originalClusters;
        }

        double maxJump = 0;
        int bestCutIndex = -1;
        for (int i = 0; i < mergeDistances.size() - 1; i++) {
            double jump = mergeDistances.get(i + 1) - mergeDistances.get(i);
            if (jump > maxJump) { maxJump = jump; bestCutIndex = i; }
        }

        double optimalThreshold = (bestCutIndex != -1) ? mergeDistances.get(bestCutIndex) + 0.0001 : mergeDistances.get(mergeDistances.size() - 1) + 0.0001;
        api.logging().logToOutput("Automatically determined merge threshold: " + String.format("%.4f", optimalThreshold));

        if (distanceMatrix.length == 2 && distanceMatrix[0][1] > 0.1) {
             api.logging().logToOutput("Sanity check triggered: Forcing two clusters for high-distance pair.");
             return originalClusters;
        }

        if (bestCutIndex != -1) {
            return clusterHistory.get(bestCutIndex);
        } else {
            // No significant jump, fall back to a single cluster if distances are close.
            // This is the state after the last merge.
            return clusterHistory.get(clusterHistory.size() - 1);
        }
    }

    private double findAverageClusterDistance(Set<Integer> c1, Set<Integer> c2, double[][] distanceMatrix) {
        double totalDistance = 0;
        int pairCount = 0;
        for (int p1 : c1) {
            for (int p2 : c2) {
                totalDistance += distanceMatrix[p1][p2];
                pairCount++;
            }
        }
        return pairCount > 0 ? totalDistance / pairCount : Double.MAX_VALUE;
    }
}