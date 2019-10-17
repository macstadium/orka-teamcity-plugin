package com.macstadium.orka;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.NamedThreadFactory;
import jetbrains.buildServer.util.ThreadUtil;
import jetbrains.buildServer.util.executors.ExecutorsFactory;

public class AsyncExecutor {
    private final ScheduledExecutorService executor;
    private final String threadPrefix;

    public AsyncExecutor(String threadPrefix) {
        this.threadPrefix = threadPrefix;
        int threadCount = TeamCityProperties.getInteger("teamcity.macstadium.orka.profile.async.threads", 2);
        this.executor = ExecutorsFactory.newFixedScheduledDaemonExecutor(this.threadPrefix, threadCount);
    }

    public Future<?> submit(final String taskName, final Runnable runnable) {
        return this.executor.submit(new Runnable() {
            public void run() {
                NamedThreadFactory.executeWithNewThreadName(taskName, runnable);
            }
        });
    }

    public void dispose() {
        ThreadUtil.shutdownNowAndWait(this.executor, this.threadPrefix);
    }
}
