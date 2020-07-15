package com.macstadium.orka;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.NamedThreadFactory;
import jetbrains.buildServer.util.ThreadUtil;
import jetbrains.buildServer.util.executors.ExecutorsFactory;

import org.jetbrains.annotations.NotNull;

public class AsyncExecutor {
    private final ScheduledExecutorService executor;
    private final String threadPrefix;

    public AsyncExecutor(String threadPrefix) {
        this.threadPrefix = threadPrefix;
        int threadCount = TeamCityProperties.getInteger("teamcity.macstadium.orka.profile.async.threads", 2);
        this.executor = ExecutorsFactory.newFixedScheduledExecutor(this.threadPrefix, threadCount);
    }

    public Future<?> submit(@NotNull final String taskName, @NotNull final Runnable runnable) {
        return this.executor.submit(new Runnable() {
            public void run() {
                NamedThreadFactory.executeWithNewThreadName(taskName, runnable);
            }
        });
    }

    public ScheduledFuture<?> scheduleWithFixedDelay(@NotNull final String taskName, @NotNull final Runnable task,
            final long initialDelay, final long delay, final TimeUnit unit) {
        return executor.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                NamedThreadFactory.executeWithNewThreadName(taskName, task);
            }
        }, initialDelay, delay, unit);
    }

    public void dispose() {
        ThreadUtil.shutdownNowAndWait(this.executor, this.threadPrefix);
    }
}
