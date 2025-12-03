package com.adaptivecache.core;

import java.util.*;

/**
 * Tracks and analyzes performance metrics for cache operations
 */
public class PerformanceMetrics {
    private int totalSearches;
    private int hits;
    private int misses;
    private long totalAccessCost;
    private long totalSearchTimeNs;
    private int insertions;
    private int evictions;
    private int strategyChanges;
    private final List<String> recentOperations = new ArrayList<>();
    private final Map<String, Integer> operationCounts = new HashMap<>();
    private long startTime;
    
    public PerformanceMetrics() {
        this.startTime = System.currentTimeMillis();
        reset();
    }
    
    public synchronized void recordHit(int accessCost) {
        totalSearches++;
        hits++;
        totalAccessCost += accessCost;
        recordOperation("HIT");
    }
    
    public synchronized void recordMiss() {
        totalSearches++;
        misses++;
        recordOperation("MISS");
    }
    
    public synchronized void recordInsertion() {
        insertions++;
        recordOperation("INSERT");
    }
    
    public synchronized void recordEviction() {
        evictions++;
        recordOperation("EVICT");
    }
    
    public synchronized void recordBulkLoad(int count) {
        insertions += count;
        recordOperation("BULK_LOAD");
    }
    
    public synchronized void recordStrategyChange(String strategyName) {
        strategyChanges++;
        recordOperation("STRATEGY_CHANGE to " + strategyName);
    }
    
    public synchronized void recordSearchTime(long timeNs) {
        totalSearchTimeNs += timeNs;
    }
    
    private synchronized void recordOperation(String operation) {
        recentOperations.add(operation);
        if (recentOperations.size() > 100) {
            recentOperations.remove(0);
        }
        operationCounts.put(operation, operationCounts.getOrDefault(operation, 0) + 1);
    }
    
    public synchronized void reset() {
        totalSearches = 0;
        hits = 0;
        misses = 0;
        totalAccessCost = 0;
        totalSearchTimeNs = 0;
        insertions = 0;
        evictions = 0;
        strategyChanges = 0;
        recentOperations.clear();
        operationCounts.clear();
        startTime = System.currentTimeMillis();
    }
    
    public synchronized PerformanceReport generateReport() {
        double hitRate = totalSearches > 0 ? (hits * 100.0) / totalSearches : 0;
        double avgAccessCost = hits > 0 ? totalAccessCost / (double) hits : 0;
        double avgSearchTimeMs = totalSearches > 0 ? 
            (totalSearchTimeNs / 1_000_000.0) / totalSearches : 0;
        double operationsPerSecond = totalSearches > 0 ? 
            totalSearches / ((System.currentTimeMillis() - startTime) / 1000.0) : 0;
        
        return new PerformanceReport(
            totalSearches, hits, misses, hitRate,
            avgAccessCost, avgSearchTimeMs, operationsPerSecond,
            insertions, evictions, strategyChanges,
            new HashMap<>(operationCounts),
            System.currentTimeMillis() - startTime
        );
    }
    
    public synchronized double getHitRate() {
        return totalSearches > 0 ? (hits * 100.0) / totalSearches : 0;
    }
    
    public synchronized int getTotalOperations() {
        return totalSearches + insertions + evictions + strategyChanges;
    }
    
    public synchronized List<String> getRecentOperations(int count) {
        int fromIndex = Math.max(0, recentOperations.size() - count);
        return new ArrayList<>(recentOperations.subList(fromIndex, recentOperations.size()));
    }
}

/**
 * Comprehensive performance report
 */
record PerformanceReport(
    int totalSearches,
    int hits,
    int misses,
    double hitRate,
    double avgAccessCost,
    double avgSearchTimeMs,
    double operationsPerSecond,
    int insertions,
    int evictions,
    int strategyChanges,
    Map<String, Integer> operationCounts,
    long uptimeMs
) {
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== PERFORMANCE REPORT ===\n");
        sb.append(String.format("Uptime: %.1f seconds\n", uptimeMs / 1000.0));
        sb.append(String.format("Total Operations: %d\n", totalSearches + insertions + evictions));
        sb.append(String.format("Hit Rate: %.2f%% (%d/%d)\n", hitRate, hits, totalSearches));
        sb.append(String.format("Avg Access Cost: %.2f steps\n", avgAccessCost));
        sb.append(String.format("Avg Search Time: %.3f ms\n", avgSearchTimeMs));
        sb.append(String.format("Operations/sec: %.1f\n", operationsPerSecond));
        sb.append(String.format("Insertions: %d, Evictions: %d\n", insertions, evictions));
        sb.append(String.format("Strategy Changes: %d\n", strategyChanges));
        
        if (!operationCounts.isEmpty()) {
            sb.append("\nOperation Counts:\n");
            operationCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> sb.append(String.format("  %-20s: %d\n", entry.getKey(), entry.getValue())));
        }
        
        return sb.toString();
    }
}
