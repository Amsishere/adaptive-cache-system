package com.adaptivecache.core;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe self-organizing linked list with pluggable optimization strategies.
 * Implements multiple cache optimization algorithms with performance tracking.
 */
public class SelfOrganizingList<T extends Comparable<T>> {
    
    private static class Node<T> {
        T data;
        Node<T> next;
        int accessCount;
        long lastAccessed;
        long insertionTime;
        
        Node(T data) {
            this.data = data;
            this.next = null;
            this.accessCount = 0;
            this.lastAccessed = System.currentTimeMillis();
            this.insertionTime = System.currentTimeMillis();
        }
        
        @Override
        public String toString() {
            return String.format("%s (accesses: %d, last: %dms)", 
                data, accessCount, System.currentTimeMillis() - lastAccessed);
        }
    }
    
    private Node<T> head;
    private int size;
    private int capacity;
    private CacheStrategy<T> strategy;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final PerformanceMetrics metrics = new PerformanceMetrics();
    private final Map<T, Node<T>> lookupTable = new HashMap<>(); // For O(1) contains check
    
    public SelfOrganizingList(int capacity, CacheStrategy<T> strategy) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.head = null;
        this.size = 0;
        this.capacity = capacity;
        this.strategy = Objects.requireNonNull(strategy, "Strategy cannot be null");
    }
    
    /**
     * Adds element with adaptive capacity management.
     * Returns true if added, false if already exists.
     */
    public boolean add(T data) {
        lock.writeLock().lock();
        try {
            // Fast duplicate check using hash map
            if (lookupTable.containsKey(data)) {
                return false;
            }
            
            // Evict if at capacity (using LRU policy)
            if (size >= capacity) {
                evictLRU();
            }
            
            // Create and insert new node at head
            Node<T> newNode = new Node<>(data);
            newNode.next = head;
            head = newNode;
            size++;
            
            // Update lookup table
            lookupTable.put(data, newNode);
            
            metrics.recordInsertion();
            return true;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Adaptive search with strategy-based reorganization.
     * Returns SearchResult containing data, access cost, and search time.
     */
    public SearchResult<T> search(T data) {
        lock.writeLock().lock();
        try {
            long startTime = System.nanoTime();
            int accessCost = 0;
            
            if (head == null) {
                metrics.recordMiss();
                return new SearchResult<>(null, accessCost, false, 
                    System.nanoTime() - startTime, "Empty list");
            }
            
            // Special case: head contains data
            accessCost++;
            if (head.data.equals(data)) {
                head.accessCount++;
                head.lastAccessed = System.currentTimeMillis();
                metrics.recordHit(accessCost);
                return new SearchResult<>(head.data, accessCost, true,
                    System.nanoTime() - startTime, "Found at head");
            }
            
            // General search
            Node<T> prev = null;
            Node<T> current = head;
            
            while (current != null) {
                accessCost++;
                
                if (current.data.equals(data)) {
                    // Found the element
                    current.accessCount++;
                    current.lastAccessed = System.currentTimeMillis();
                    
                    // Apply optimization strategy
                    String operation = strategy.reorganize(this, prev, current);
                    
                    metrics.recordHit(accessCost);
                    return new SearchResult<>(current.data, accessCost, true,
                        System.nanoTime() - startTime, operation);
                }
                
                prev = current;
                current = current.next;
            }
            
            // Not found
            metrics.recordMiss();
            return new SearchResult<>(null, accessCost, false,
                System.nanoTime() - startTime, "Element not found");
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Bulk load data from collection with deduplication
     */
    public void loadAll(Collection<T> data) {
        lock.writeLock().lock();
        try {
            for (T item : data) {
                add(item);
            }
            metrics.recordBulkLoad(data.size());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Gets current performance report
     */
    public PerformanceReport getPerformanceReport() {
        return metrics.generateReport();
    }
    
    /**
     * Clears all elements from the list
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            head = null;
            size = 0;
            lookupTable.clear();
            metrics.reset();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Changes the optimization strategy at runtime
     */
    public void setStrategy(CacheStrategy<T> strategy) {
        lock.writeLock().lock();
        try {
            this.strategy = strategy;
            metrics.recordStrategyChange(strategy.getName());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Returns list elements as ordered list
     */
    public List<T> toList() {
        lock.readLock().lock();
        try {
            List<T> result = new ArrayList<>();
            Node<T> current = head;
            while (current != null) {
                result.add(current.data);
                current = current.next;
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    private void evictLRU() {
        if (head == null) return;
        
        // Single element case
        if (head.next == null) {
            lookupTable.remove(head.data);
            head = null;
            size = 0;
            metrics.recordEviction();
            return;
        }
        
        // Find least recently used (excluding head for efficiency)
        Node<T> prev = head;
        Node<T> lruPrev = null;
        Node<T> current = head.next;
        long oldest = head.lastAccessed;
        
        while (current != null) {
            if (current.lastAccessed < oldest) {
                oldest = current.lastAccessed;
                lruPrev = prev;
            }
            prev = current;
            current = current.next;
        }
        
        // Remove LRU element
        if (lruPrev != null) {
            Node<T> toRemove = lruPrev.next;
            lookupTable.remove(toRemove.data);
            lruPrev.next = toRemove.next;
        } else {
            // Head is LRU
            lookupTable.remove(head.data);
            head = head.next;
        }
        
        size--;
        metrics.recordEviction();
    }
    
    // Package-private accessors for strategies
    Node<T> getHead() { return head; }
    void setHead(Node<T> newHead) { this.head = newHead; }
    Map<T, Node<T>> getLookupTable() { return lookupTable; }
    
    // Public accessors
    public int size() { return size; }
    public int getCapacity() { return capacity; }
    public String getStrategyName() { return strategy.getName(); }
    public boolean isEmpty() { return size == 0; }
    
    @Override
    public String toString() {
        lock.readLock().lock();
        try {
            StringBuilder sb = new StringBuilder("[");
            Node<T> current = head;
            while (current != null) {
                sb.append(current.data);
                if (current.next != null) sb.append(" → ");
                current = current.next;
            }
            sb.append("]");
            return sb.toString();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Detailed string representation with access counts
     */
    public String toDetailedString() {
        lock.readLock().lock();
        try {
            StringBuilder sb = new StringBuilder("SelfOrganizingList {\n");
            sb.append("  Strategy: ").append(strategy.getName()).append("\n");
            sb.append("  Size: ").append(size).append("/").append(capacity).append("\n");
            sb.append("  Elements: \n");
            
            Node<T> current = head;
            int position = 0;
            while (current != null) {
                sb.append(String.format("    [%d] %s\n", 
                    position++, current.toString()));
                current = current.next;
            }
            
            sb.append("  Performance: ").append(metrics.getHitRate()).append("% hit rate\n");
            sb.append("}");
            return sb.toString();
        } finally {
            lock.readLock().unlock();
        }
    }
}

/**
 * Search result with detailed metrics
 */
class SearchResult<T> {
    private final T data;
    private final int accessCost;
    private final boolean found;
    private final long searchTimeNs;
    private final String operation;
    
    public SearchResult(T data, int accessCost, boolean found, 
                       long searchTimeNs, String operation) {
        this.data = data;
        this.accessCost = accessCost;
        this.found = found;
        this.searchTimeNs = searchTimeNs;
        this.operation = operation;
    }
    
    public T getData() { return data; }
    public int getAccessCost() { return accessCost; }
    public boolean isFound() { return found; }
    public long getSearchTimeNs() { return searchTimeNs; }
    public double getSearchTimeMs() { return searchTimeNs / 1_000_000.0; }
    public String getOperation() { return operation; }
    
    @Override
    public String toString() {
        return String.format("SearchResult{found=%s, cost=%d, time=%.3fms, op=%s}", 
            found, accessCost, getSearchTimeMs(), operation);
    }
}
