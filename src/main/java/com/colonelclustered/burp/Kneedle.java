package com.colonelclustered.burp;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements the Kneedle algorithm for finding the "knee" or "elbow" of a curve.
 * This is used to automatically and reliably select the epsilon parameter for DBSCAN.
 * Based on the paper: "Finding a Kneedle in a Haystack" by Satopaa, et al.
 */
public class Kneedle {

    private static class Point {
        double x, y;
        Point(double x, double y) { this.x = x; this.y = y; }
    }

    public static int findElbowIndex(List<Double> yData) {
        if (yData == null || yData.size() < 2) {
            return -1;
        }

        // 1. Normalize the data
        List<Point> points = new ArrayList<>();
        double minX = 0, maxX = yData.size() - 1;
        double minY = yData.get(0), maxY = yData.get(yData.size() - 1);

        for (int i = 0; i < yData.size(); i++) {
            double normalizedX = (i - minX) / (maxX - minX);
            double normalizedY = (yData.get(i) - minY) / (maxY - minY);
            points.add(new Point(normalizedX, normalizedY));
        }

        // 2. Calculate the perpendicular distance from each point to the line
        // connecting the first and last points.
        Point first = points.get(0);
        Point last = points.get(points.size() - 1);
        double maxDistance = -1;
        int elbowIndex = -1;

        for (int i = 1; i < points.size() - 1; i++) {
            Point p = points.get(i);
            double distance = perpendicularDistance(p, first, last);
            if (distance > maxDistance) {
                maxDistance = distance;
                elbowIndex = i;
            }
        }
        
        return elbowIndex;
    }

    private static double perpendicularDistance(Point p, Point lineStart, Point lineEnd) {
        double dx = lineEnd.x - lineStart.x;
        double dy = lineEnd.y - lineStart.y;
        
        if (dx == 0 && dy == 0) { // The line is just a point
            return Math.sqrt(Math.pow(p.x - lineStart.x, 2) + Math.pow(p.y - lineStart.y, 2));
        }

        double numerator = Math.abs(dy * p.x - dx * p.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x);
        double denominator = Math.sqrt(dy * dy + dx * dx);
        
        return numerator / denominator;
    }
}