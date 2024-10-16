package net.lenni0451.noteblockbot.task;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A task queue that executes tasks asynchronously on a by-guild basis.<br>
 * It is used to fairly spread the load of tasks between all guilds and to prevent that one guild can block the execution of tasks for other guilds.<br>
 * Proper rate limiting is still required for tasks that use a lot of resources/have a long execution time.
 */
@Slf4j
public class TaskQueue {

    private final Map<Long, List<GuildTasks>> tasks = new HashMap<>();
    private final BlockingQueue<Long> guildQueue = new LinkedBlockingQueue<>();

    public TaskQueue() {
        Thread thread = new Thread(this::runTasks, "TaskQueue");
        thread.setDaemon(true);
        thread.start();
    }

    public void add(final long guildId, final List<Runnable> tasks, final Runnable finishHandler) {
        if (tasks.isEmpty()) return;
        GuildTasks guildTasks = new GuildTasks(guildId, new ArrayDeque<>(tasks), finishHandler);
        synchronized (this.tasks) {
            this.tasks.computeIfAbsent(guildId, k -> new ArrayList<>()).add(guildTasks);
        }
        this.guildQueue.add(guildId);
    }

    private void runTasks() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Set<Long> guildIds = new LinkedHashSet<>();
                guildIds.add(this.guildQueue.take());
                this.guildQueue.drainTo(guildIds);

                Iterator<Long> it = guildIds.iterator();
                while (it.hasNext()) {
                    Long guildId = it.next();
                    GuildTasks guildTasks;
                    synchronized (this.tasks) {
                        List<GuildTasks> guildTasksList = this.tasks.get(guildId);
                        if (guildTasksList == null) {
                            it.remove();
                            continue;
                        }
                        if (guildTasksList.isEmpty()) {
                            this.tasks.remove(guildId);
                            it.remove();
                            continue;
                        }
                        guildTasks = guildTasksList.get(0);
                        if (guildTasks.tasks.size() <= 1) {
                            guildTasksList.remove(0);
                            if (guildTasksList.isEmpty()) {
                                this.tasks.remove(guildId);
                                it.remove();
                            }
                        }
                    }

                    Runnable nextTask = guildTasks.tasks.poll();
                    if (nextTask == null) continue;
                    try {
                        nextTask.run();
                    } catch (Throwable t) {
                        log.error("An error occurred while executing task for guild {}", guildTasks.guildId, t);
                    }
                    if (guildTasks.tasks.isEmpty()) {
                        try {
                            guildTasks.finishHandler.run();
                        } catch (Throwable t) {
                            log.error("An error occurred while executing finish handler for guild {}", guildTasks.guildId, t);
                        }
                    }
                }
                this.guildQueue.addAll(guildIds);
            } catch (InterruptedException e) {
                break;
            } catch (Throwable t) {
                log.error("An error occurred while executing tasks", t);
            }
        }
    }


    private record GuildTasks(long guildId, Queue<Runnable> tasks, Runnable finishHandler) {
    }

}
