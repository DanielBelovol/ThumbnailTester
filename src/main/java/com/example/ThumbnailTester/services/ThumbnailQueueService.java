package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.dto.ThumbnailQueue;
import com.example.ThumbnailTester.dto.ThumbnailQueueItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ThumbnailQueueService {
    private ThumbnailQueue thumbnailQueue;

    public ThumbnailQueueService() {
        this.thumbnailQueue = new ThumbnailQueue();
    }

    // Add a new item to the queue
    public void add(ThumbnailQueueItem item) {
        thumbnailQueue.add(item);
    }

    // Delete a specific item from the queue
    public boolean delete(ThumbnailQueueItem item) {
        return thumbnailQueue.delete(item);
    }

    // Retrieve and remove the next item from the queue
    public ThumbnailQueueItem next() {
        return thumbnailQueue.poll();
    }

    // Check if the queue is empty
    public boolean isEmpty() {
        return thumbnailQueue.isEmpty();
    }
    public ThumbnailQueue getThumbnailQueue() {
        return thumbnailQueue;
    }

    public void setThumbnailQueue(ThumbnailQueue thumbnailQueue) {
        this.thumbnailQueue = thumbnailQueue;
    }
}
