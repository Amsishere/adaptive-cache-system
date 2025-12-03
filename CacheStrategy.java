package com.adaptivecache.core;

/**
 * Strategy interface for cache reorganization algorithms.
 * Each strategy defines how to reorganize the list after an element is accessed.
 */
public interface CacheStrategy<T> {
    
    /**
     * Reorganizes the list after accessing an element.
     * @param list The self-organizing list
     * @param prev Previous node (null if current is head)
     * @param current The accessed node
     * @return Description of the operation performed
     */
    String reorganize(SelfOrganizingList<T> list, Node<T> prev, Node<T> current);
    
    /**
     * Gets the name of the strategy
     */
    String getName();
    
    /**
     * Gets a description of the strategy
     */
    String getDescription();
    
    /**
     * Gets the time complexity of the strategy
     */
    default String getTimeComplexity() {
        return "O(1)";
    }
}

/**
 * Move-to-Front Strategy: Moves accessed element to front of list
 * Best for temporal locality access patterns
 */
class MoveToFrontStrategy<T> implements CacheStrategy<T> {
    @Override
    public String reorganize(SelfOrganizingList<T> list, Node<T> prev, Node<T> current) {
        if (prev != null) {
            // Remove current from its position
            prev.next = current.next;
            // Move current to front
            current.next = list.getHead();
            list.setHead(current);
            return "Moved to front";
        }
        return "Already at front";
    }
    
    @Override public String getName() { return "Move-to-Front (MTF)"; }
    @Override public String getDescription() { 
        return "Moves accessed element to list head. Excellent for temporal locality."; 
    }
}

/**
 * Transpose Strategy: Swaps accessed element with its predecessor
 * Good for sequential access patterns
 */
class TransposeStrategy<T> implements CacheStrategy<T> {
    @Override
    public String reorganize(SelfOrganizingList<T> list, Node<T> prev, Node<T> current) {
        if (prev == null) {
            return "Already at head (no transpose)";
        }
        
        // Find node before prev
        if (list.getHead() == prev) {
            // Current is second element, swap with head
            prev.next = current.next;
            current.next = prev;
            list.setHead(current);
            return "Transposed with head";
        } else {
            // General case: find node before prev
            Node<T> prevPrev = list.getHead();
            while (prevPrev != null && prevPrev.next != prev) {
                prevPrev = prevPrev.next;
            }
            
            if (prevPrev != null) {
                // Swap prev and current
                prev.next = current.next;
                current.next = prev;
                prevPrev.next = current;
                return "Transposed with predecessor";
            }
        }
        
        return "No transposition performed";
    }
    
    @Override public String getName() { return "Transpose"; }
    @Override public String getDescription() { 
        return "Swaps accessed element with its predecessor. Good for sequential access."; 
    }
}

/**
 * Frequency Count Strategy: Orders elements by access frequency
 * Best for skewed access distributions (80-20 rule)
 */
class FrequencyCountStrategy<T> implements CacheStrategy<T> {
    @Override
    public String reorganize(SelfOrganizingList<T> list, Node<T> prev, Node<T> current) {
        if (prev == null) {
            return "Already at head (frequency unchanged)";
        }
        
        // Remove current from its position
        prev.next = current.next;
        
        // Find new position based on access count
        if (list.getHead().accessCount <= current.accessCount) {
            // Move to head
            current.next = list.getHead();
            list.setHead(current);
            return "Moved to head (higher frequency)";
        } else {
            // Find appropriate position
            Node<T> searchPrev = list.getHead();
            Node<T> search = list.getHead().next;
            
            while (search != null && search.accessCount > current.accessCount) {
                searchPrev = search;
                search = search.next;
            }
            
            // Insert at found position
            current.next = search;
            searchPrev.next = current;
            return String.format("Moved to position (frequency: %d)", current.accessCount);
        }
    }
    
    @Override public String getName() { return "Frequency Count"; }
    @Override public String getDescription() { 
        return "Orders elements by access frequency. Best for skewed distributions."; 
    }
    @Override public String getTimeComplexity() { return "O(n)"; }
}

/**
 * LRU Strategy: Moves accessed element to head (simplified LRU)
 * Good for general-purpose caching
 */
class LRUStrategy<T> implements CacheStrategy<T> {
    @Override
    public String reorganize(SelfOrganizingList<T> list, Node<T> prev, Node<T> current) {
        // LRU is similar to MTF but with additional aging considerations
        if (prev != null) {
            prev.next = current.next;
            current.next = list.getHead();
            list.setHead(current);
            return "Moved to head (LRU)";
        }
        return "Already at head (LRU)";
    }
    
    @Override public String getName() { return "LRU (Least Recently Used)"; }
    @Override public String getDescription() { 
        return "Moves accessed element to head. Simple LRU implementation."; 
    }
}
