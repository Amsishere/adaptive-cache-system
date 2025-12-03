package com.adaptivecache.analyzer;

import com.adaptivecache.core.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Comprehensive performance analysis across strategies and access patterns
 */
public class PerformanceAnalyzer {
    
    public enum AccessPattern {
        RANDOM("Random Uniform", "Equal probability for all elements"),
        SEQUENTIAL("Sequential", "Access elements in order"),
        ZIPFIAN("Zipfian (80-20)", "80% of accesses to 20% of elements"),
        TEMPORAL_LOCALITY("Temporal Locality", "Recently accessed elements are more likely"),
        GAUSSIAN("Gaussian", "Accesses cluster around a mean value");
        
        private final String name;
        private final String description;
        
        AccessPattern(String name, String description) {
            this.name = name;
            this.description = description;
        }
        
        public String getName() { return name; }
        public String getDescription() { return description; }
    }
    
    public static class BenchmarkConfig {
        private final int listSize;
        private final int cacheSize;
        private final List<CacheStrategy<Integer>> strategies;
        private final AccessPattern pattern;
        private final int operations;
        private final int warmupOperations;
        private final boolean verbose;
        
        private BenchmarkConfig(Builder builder) {
            this.listSize = builder.listSize;
            this.cacheSize = builder.cacheSize;
            this.strategies = builder.strategies;
            this.pattern = builder.pattern;
            this.operations = builder.operations;
            this.warmupOperations = builder.warmupOperations;
            this.verbose = builder.verbose;
        }
        
        public static class Builder {
            private int listSize = 1000;
            private int cacheSize = 100;
            private List<CacheStrategy<Integer>> strategies = Arrays.asList(
                new MoveToFrontStrategy<>(),
                new TransposeStrategy<>(),
                new FrequencyCountStrategy<>(),
                new LRUStrategy<>()
            );
            private AccessPattern pattern = AccessPattern.ZIPFIAN;
            private int operations = 10000;
            private int warmupOperations = 1000;
            private boolean verbose = false;
            
            public Builder listSize(int size) { this.listSize = size; return this; }
            public Builder cacheSize(int size) { this.cacheSize = size; return this; }
            public Builder strategies(List<CacheStrategy<Integer>> strategies) { 
                this.strategies = strategies; return this; 
            }
            public Builder pattern(AccessPattern pattern) { this.pattern = pattern; return this; }
            public Builder operations(int ops) { this.operations = ops; return this; }
            public Builder warmupOperations(int ops) { this.warmupOperations = ops; return this; }
            public Builder verbose(boolean verbose) { this.verbose = verbose; return this; }
            public BenchmarkConfig build() { return new BenchmarkConfig(this); }
        }
    }
    
    /**
     * Runs comprehensive benchmark comparing all strategies
     */
    public List<BenchmarkResult> runBenchmark(BenchmarkConfig config) {
        List<BenchmarkResult> results = new ArrayList<>();
        
        System.out.println("=== Starting Benchmark ===");
        System.out.println("List Size: " + config.listSize);
        System.out.println("Cache Size: " + config.cacheSize);
        System.out.println("Access Pattern: " + config.pattern.getName());
        System.out.println("Operations: " + config.operations);
        System.out.println("Strategies: " + config.strategies.stream()
            .map(CacheStrategy::getName).collect(Collectors.joining(", ")));
        
        for (CacheStrategy<Integer> strategy : config.strategies) {
            System.out.println("\nTesting " + strategy.getName() + "...");
            
            // Create fresh list for this strategy
            SelfOrganizingList<Integer> list = 
                new SelfOrganizingList<>(config.cacheSize, strategy);
            
            // Initialize with data
            List<Integer> initialData = DataGenerator.generateData(config.listSize);
            list.loadAll(initialData);
            
            // Generate search sequence
            List<Integer> searchSequence = DataGenerator.generateSearchSequence(
                initialData, config.operations + config.warmupOperations, config.pattern);
            
            // Warmup phase (not measured)
            for (int i = 0; i < config.warmupOperations; i++) {
                list.search(searchSequence.get(i));
            }
            
            // Measurement phase
            long startTime = System.nanoTime();
            int totalAccessCost = 0;
            int measuredHits = 0;
            
            for (int i = config.warmupOperations; i < searchSequence.size(); i++) {
                SearchResult<Integer> result = list.search(searchSequence.get(i));
                totalAccessCost += result.getAccessCost();
                if (result.isFound()) measuredHits++;
                
                if (config.verbose && i % 1000 == 0) {
                    System.out.printf("  Progress: %d/%d operations\n", 
                        i - config.warmupOperations, config.operations);
                }
            }
            
            long endTime = System.nanoTime();
            long totalTimeNs = endTime - startTime;
            
            // Collect results
            PerformanceReport report = list.getPerformanceReport();
            BenchmarkResult benchmarkResult = new BenchmarkResult(
                strategy.getName(),
                config.pattern,
                report.hitRate(),
                totalAccessCost / (double) config.operations,
                totalTimeNs / 1_000_000.0,
                report.avgSearchTimeMs(),
                measuredHits,
                config.operations - measuredHits,
                report
            );
            
            results.add(benchmarkResult);
            
            if (config.verbose) {
                System.out.println("  " + benchmarkResult.toShortString());
            }
        }
        
        return results;
    }
    
    /**
     * Finds the best strategy for given access pattern
     */
    public String recommendStrategy(List<BenchmarkResult> results) {
        return results.stream()
            .max(Comparator.comparingDouble(BenchmarkResult::hitRate))
            .map(r -> r.strategyName() + " (Hit Rate: " + String.format("%.2f", r.hitRate()) + "%)")
            .orElse("No results");
    }
    
    /**
     * Generates comparative analysis
     */
    public String generateAnalysis(List<BenchmarkResult> results) {
        StringBuilder analysis = new StringBuilder();
        analysis.append("=== COMPARATIVE ANALYSIS ===\n\n");
        
        // Sort by hit rate
        results.sort(Comparator.comparingDouble(BenchmarkResult::hitRate).reversed());
        
        for (int i = 0; i < results.size(); i++) {
            BenchmarkResult r = results.get(i);
            analysis.append(String.format("%d. %s\n", i + 1, r.strategyName()));
            analysis.append(String.format("   Hit Rate: %.2f%%\n", r.hitRate()));
            analysis.append(String.format("   Avg Access Cost: %.2f steps\n", r.avgAccessCost()));
            analysis.append(String.format("   Avg Search Time: %.3f ms\n", r.avgSearchTimeMs()));
            analysis.append(String.format("   Total Time: %.2f ms\n", r.totalTimeMs()));
            analysis.append("\n");
        }
        
        // Find best for different metrics
        BenchmarkResult bestHitRate = results.get(0);
        BenchmarkResult bestAccessCost = results.stream()
            .min(Comparator.comparingDouble(BenchmarkResult::avgAccessCost))
            .orElse(results.get(0));
        BenchmarkResult bestSearchTime = results.stream()
            .min(Comparator.comparingDouble(BenchmarkResult::avgSearchTimeMs))
            .orElse(results.get(0));
        
        analysis.append("=== RECOMMENDATIONS ===\n");
        analysis.append(String.format("For maximum hit rate: %s (%.2f%%)\n", 
            bestHitRate.strategyName(), bestHitRate.hitRate()));
        analysis.append(String.format("For lowest access cost: %s (%.2f steps)\n", 
            bestAccessCost.strategyName(), bestAccessCost.avgAccessCost()));
        analysis.append(String.format("For fastest search: %s (%.3f ms)\n", 
            bestSearchTime.strategyName(), bestSearchTime.avgSearchTimeMs()));
        
        return analysis.toString();
    }
}

/**
 * Data generation utilities
 */
class DataGenerator {
    private static final Random random = new Random(42); // Fixed seed for reproducibility
    
    public static List<Integer> generateData(int size) {
        List<Integer> data = new ArrayList<>();
        for (int i = 1; i <= size; i++) {
            data.add(i);
        }
        Collections.shuffle(data, random);
        return data;
    }
    
    public static List<Integer> generateSearchSequence(List<Integer> data, int count, 
                                                      PerformanceAnalyzer.AccessPattern pattern) {
        List<Integer> sequence = new ArrayList<>();
        
        switch (pattern) {
            case RANDOM:
                for (int i = 0; i < count; i++) {
                    sequence.add(data.get(random.nextInt(data.size())));
                }
                break;
                
            case SEQUENTIAL:
                int current = 0;
                for (int i = 0; i < count; i++) {
                    sequence.add(data.get(current));
                    current = (current + 1) % data.size();
                }
                break;
                
            case ZIPFIAN:
                // 80-20 rule implementation
                int hotZoneSize = data.size() / 5; // 20% hot items
                for (int i = 0; i < count; i++) {
                    if (random.nextDouble() < 0.8) {
                        // Access hot items (80% of accesses)
                        sequence.add(data.get(random.nextInt(hotZoneSize)));
                    } else {
                        // Access cold items (20% of accesses)
                        sequence.add(data.get(hotZoneSize + random.nextInt(data.size() - hotZoneSize)));
                    }
                }
                break;
                
            case TEMPORAL_LOCALITY:
                // Markov chain model: high probability to access recent elements
                List<Integer> recent = new ArrayList<>();
                int windowSize = 10;
                
                for (int i = 0; i < count; i++) {
                    if (!recent.isEmpty() && random.nextDouble() < 0.7) {
                        // Access recent element
                        sequence.add(recent.get(random.nextInt(recent.size())));
                    } else {
                        // Access random element
                        Integer element = data.get(random.nextInt(data.size()));
                        sequence.add(element);
                        recent.add(element);
                        if (recent.size() > windowSize) {
                            recent.remove(0);
                        }
                    }
                }
                break;
                
            case GAUSSIAN:
                // Gaussian distribution around mean
                int mean = data.size() / 2;
                double stdDev = data.size() / 6.0; // ~99.7% within range
                
                for (int i = 0; i < count; i++) {
                    int index;
                    do {
                        index = (int) (random.nextGaussian() * stdDev + mean);
                    } while (index < 0 || index >= data.size());
                    
                    sequence.add(data.get(index));
                }
                break;
        }
        
        return sequence;
    }
}

record BenchmarkResult(
    String strategyName,
    PerformanceAnalyzer.AccessPattern pattern,
    double hitRate,
    double avgAccessCost,
    double totalTimeMs,
    double avgSearchTimeMs,
    int hits,
    int misses,
    PerformanceReport details
) {
    public String toShortString() {
        return String.format("%s: %.2f%% hit rate, %.2f avg cost, %.2f ms total", 
            strategyName, hitRate, avgAccessCost, totalTimeMs);
    }
}
