package com.colonelclustered.burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.correlation.Covariance;
import smile.clustering.DBSCAN;
import smile.math.distance.EuclideanDistance;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ClusteringEngine {

    public Map<Integer, List<HttpRequestResponse>> clusterResponses(List<HttpRequestResponse> requestResponses, MontoyaApi api, Consumer<String> progressCallback) {
        List<String> responses = requestResponses.stream()
                .map(HttpRequestResponse::response)
                .map(response -> api.utilities().byteUtils().convertToString(response.body().getBytes()))
                .collect(Collectors.toList());

        // 1. TF-IDF
        progressCallback.accept("Step 1/4: Performing TF-IDF vectorization...");
        List<List<String>> tokenizedDocuments = responses.stream()
                .map(this::tokenize)
                .collect(Collectors.toList());

        Set<String> vocabulary = tokenizedDocuments.stream().flatMap(Collection::stream).collect(Collectors.toSet());
        Map<String, Double> idf = calculateIDF(tokenizedDocuments, vocabulary);
        double[][] tfidfVectors = calculateTFIDF(tokenizedDocuments, idf, vocabulary);

        // 2. PCA
        progressCallback.accept("Step 2/4: Reducing dimensions with PCA...");
        if (tfidfVectors.length == 0 || tfidfVectors[0].length < 2) {
            progressCallback.accept("Error: Not enough data for PCA.");
            return Collections.emptyMap();
        }
        RealMatrix matrix = new Array2DRowRealMatrix(tfidfVectors);
        for (int j = 0; j < matrix.getColumnDimension(); j++) {
            double mean = matrix.getColumnVector(j).getNorm() / matrix.getColumnDimension();
            for (int i = 0; i < matrix.getRowDimension(); i++) {
                matrix.addToEntry(i, j, -mean);
            }
        }
        Covariance covariance = new Covariance(matrix);
        RealMatrix covarianceMatrix = covariance.getCovarianceMatrix();
        EigenDecomposition ed = new EigenDecomposition(covarianceMatrix);

        int numComponents = Math.min(2, ed.getRealEigenvalues().length);
        RealMatrix projection = ed.getV().getSubMatrix(0, covarianceMatrix.getColumnDimension() - 1, 0, numComponents - 1);
        RealMatrix projectedData = matrix.multiply(projection);
        double[][] projectedDataArray = projectedData.getData();

        // 3. Automated Epsilon Tuning
        progressCallback.accept("Step 3/4: Automatically determining optimal epsilon...");
        int minPts = 4;
        double epsilon = findOptimalEpsilon(projectedDataArray, minPts);
        progressCallback.accept("Found optimal epsilon: " + String.format("%.4f", epsilon));

        // 4. DBSCAN
        progressCallback.accept("Step 4/4: Clustering with DBSCAN...");
        DBSCAN<double[]> dbscan = DBSCAN.fit(projectedDataArray, minPts, epsilon);

        progressCallback.accept("Formatting results...");
        Map<Integer, List<HttpRequestResponse>> clusteredResponses = new HashMap<>();
        for (int i = 0; i < dbscan.y.length; i++) {
            int clusterId = dbscan.y[i];
            clusteredResponses.computeIfAbsent(clusterId, k -> new ArrayList<>()).add(requestResponses.get(i));
        }
        return clusteredResponses;
    }

    private double findOptimalEpsilon(double[][] data, int k) {
        if (data.length <= k) {
            return 0.1;
        }
        double[] kDistances = new double[data.length];
        EuclideanDistance dist = new EuclideanDistance();

        for (int i = 0; i < data.length; i++) {
            double[] point = data[i];
            List<Double> distances = new ArrayList<>();
            for (int j = 0; j < data.length; j++) {
                if (i == j) continue;
                distances.add(dist.d(point, data[j]));
            }
            Collections.sort(distances);
            if (distances.size() >= k) {
                kDistances[i] = distances.get(k - 1);
            }
        }
        Arrays.sort(kDistances);
        
        double maxDist = -1;
        int kneeIndex = -1;
        double x1 = 0, y1 = kDistances[0];
        double x2 = kDistances.length - 1, y2 = kDistances[kDistances.length - 1];

        for (int i = 0; i < kDistances.length; i++) {
            double x0 = i, y0 = kDistances[i];
            double distance = Math.abs((y2 - y1) * x0 - (x2 - x1) * y0 + x2 * y1 - y2 * x1) / Math.sqrt(Math.pow(y2 - y1, 2) + Math.pow(x2 - x1, 2));
            if (distance > maxDist) {
                maxDist = distance;
                kneeIndex = i;
            }
        }

        return kDistances[kneeIndex];
    }

    private List<String> tokenize(String text) {
        return Arrays.asList(text.toLowerCase().split("\\s+"));
    }

    private Map<String, Double> calculateIDF(List<List<String>> documents, Set<String> vocabulary) {
        Map<String, Double> idf = new HashMap<>();
        int totalDocuments = documents.size();
        for (String term : vocabulary) {
            long docFrequency = documents.stream().filter(doc -> doc.contains(term)).count();
            idf.put(term, Math.log((double) totalDocuments / (1 + docFrequency)));
        }
        return idf;
    }

    private double[][] calculateTFIDF(List<List<String>> documents, Map<String, Double> idf, Set<String> vocabulary) {
        List<String> vocabList = new ArrayList<>(vocabulary);
        double[][] tfidfMatrix = new double[documents.size()][vocabulary.size()];
        for (int i = 0; i < documents.size(); i++) {
            List<String> doc = documents.get(i);
            Map<String, Long> tf = doc.stream().collect(Collectors.groupingBy(e -> e, Collectors.counting()));
            for (int j = 0; j < vocabList.size(); j++) {
                String term = vocabList.get(j);
                double tfValue = (double) tf.getOrDefault(term, 0L) / doc.size();
                double idfValue = idf.getOrDefault(term, 0.0);
                tfidfMatrix[i][j] = tfValue * idfValue;
            }
        }
        return tfidfMatrix;
    }
}