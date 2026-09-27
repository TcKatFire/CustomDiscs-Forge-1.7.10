package me.navoei.customdiscs;

import java.util.concurrent.ConcurrentLinkedQueue;

public final class ServerTaskQueue {
    private static final ConcurrentLinkedQueue<Runnable> TASKS =
            new ConcurrentLinkedQueue<Runnable>();

    private ServerTaskQueue() {
    }

    public static void enqueue(Runnable task) {
        TASKS.add(task);
    }

    public static void drain() {
        Runnable task;
        while ((task = TASKS.poll()) != null) {
            task.run();
        }
    }
}
