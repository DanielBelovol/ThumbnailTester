package com.example.ThumbnailTester.dto;

import lombok.Data;

import java.util.Queue;

@Data
public class ThumbnailQueue {
    Queue <ThumbnailQueueItem> queue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    // Add a new item to the queue
    public void add(ThumbnailQueueItem item) {
        queue.add(item);
    }

    // Delete a specific item from the queue
    public boolean delete(ThumbnailQueueItem item) {
        return queue.remove(item);
    }

    // Retrieve and remove the next item from the queue
    public ThumbnailQueueItem poll() {
        return queue.poll();
    }
    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
