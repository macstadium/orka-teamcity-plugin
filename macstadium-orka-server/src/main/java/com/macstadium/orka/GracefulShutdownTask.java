package com.macstadium.orka;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.log.Loggers;

import java.util.ArrayList;
import java.util.List;

/**
 * Background task that monitors legacy instances and triggers graceful shutdown.
 * Legacy instances are VMs from previous profile configuration that should be stopped
 * after their current builds complete.
 * 
 * The task checks each legacy instance and:
 * 1. If status is RUNNING and not marked for termination → set SCHEDULED_TO_STOP
 * 2. TeamCity will automatically wait for builds to finish
 * 3. When agent becomes idle, terminateInstance() will be called
 */
public class GracefulShutdownTask implements Runnable {
    private static final Logger LOG = Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT + OrkaConstants.TYPE);

    private final OrkaCloudClient client;
    private final String profileId;

    public GracefulShutdownTask(OrkaCloudClient client, String profileId) {
        this.client = client;
        this.profileId = profileId;
    }

    @Override
    public void run() {
        LOG.debug(String.format("[%s] Running graceful shutdown check...", profileId));

        try {
            List<OrkaLegacyCloudInstance> instancesToShutdown = new ArrayList<>();

            // Collect legacy instances that need graceful shutdown
            for (jetbrains.buildServer.clouds.CloudImage image : client.getImages()) {
                if (image instanceof OrkaCloudImage) {
                    OrkaCloudImage orkaImage = (OrkaCloudImage) image;
                    for (OrkaLegacyCloudInstance legacy : orkaImage.getLegacyInstances()) {
                        if (legacy.isPendingGracefulShutdown() && !legacy.isMarkedForTermination()) {
                            instancesToShutdown.add(legacy);
                        }
                    }
                }
            }

            if (instancesToShutdown.isEmpty()) {
                LOG.debug(String.format("[%s] No legacy instances pending graceful shutdown", profileId));
                return;
            }

            LOG.info(String.format("[%s] Found %d legacy instance(s) pending graceful shutdown",
                    profileId, instancesToShutdown.size()));

            // Set SCHEDULED_TO_STOP status for each legacy instance
            // TeamCity will wait for builds to finish before calling terminateInstance()
            for (OrkaLegacyCloudInstance legacy : instancesToShutdown) {
                InstanceStatus currentStatus = legacy.getStatus();

                if (currentStatus == InstanceStatus.RUNNING) {
                    LOG.info(String.format("[%s] Scheduling graceful shutdown for legacy instance: %s",
                            profileId, legacy.getInstanceId()));

                    // Set to SCHEDULED_TO_STOP - TeamCity handles waiting for builds
                    legacy.setStatus(InstanceStatus.SCHEDULED_TO_STOP);
                    legacy.setPendingGracefulShutdown(false);
                    legacy.setMarkedForTermination(true);

                    // Schedule termination
                    // Note: We don't call terminateInstance directly because TeamCity
                    // will handle the timing based on agent build status
                    LOG.info(String.format("[%s] Legacy instance %s scheduled for termination " +
                            "(will stop after current builds complete)",
                            profileId, legacy.getInstanceId()));

                } else if (currentStatus == InstanceStatus.SCHEDULED_TO_STOP
                        || currentStatus == InstanceStatus.STOPPING
                        || currentStatus == InstanceStatus.STOPPED
                        || currentStatus == InstanceStatus.ERROR) {
                    // Already being stopped or stopped
                    LOG.debug(String.format("[%s] Legacy instance %s already in shutdown state: %s",
                            profileId, legacy.getInstanceId(), currentStatus));
                    legacy.setPendingGracefulShutdown(false);
                }
            }
        } catch (Exception e) {
            LOG.warn(String.format("[%s] Error during graceful shutdown check: %s",
                    profileId, e.getMessage()));
        }

        LOG.debug(String.format("[%s] Graceful shutdown check completed", profileId));
    }
}

