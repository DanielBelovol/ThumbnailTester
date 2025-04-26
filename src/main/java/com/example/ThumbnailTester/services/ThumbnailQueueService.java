package com.example.ThumbnailTester.services;

import com.example.ThumbnailTester.dto.ThumbnailQueue;
import com.example.ThumbnailTester.dto.ThumbnailQueueItem;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ThumbnailQueueService {
    private final Map<String, ThumbnailQueue> thumbnailQueues = new ConcurrentHashMap<>();

    public ThumbnailQueue getQueue(String videoUrl) {
        return thumbnailQueues.computeIfAbsent(videoUrl, k -> new ThumbnailQueue());
    }

    public void addToQueue(String videoUrl, ThumbnailQueueItem item) {
        getQueue(videoUrl).add(item);
    }

    public ThumbnailQueueItem pollFromQueue(String videoUrl) {
        ThumbnailQueue queue = thumbnailQueues.get(videoUrl);
        if (queue == null) return null;
        return queue.poll();
    }

    public ThumbnailQueue findItemByVideoUrl(String videoUrl) {
        ThumbnailQueue queue = thumbnailQueues.get(videoUrl);
        return queue;
    }

    public void deleteFromQueue(String videoUrl, ThumbnailQueueItem item) {
        ThumbnailQueue queue = thumbnailQueues.get(videoUrl);
        if (queue != null) {
            queue.delete(item);
        }
    }

    public void clearQueue(String videoUrl) {
        thumbnailQueues.remove(videoUrl);
    }
}
