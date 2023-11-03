package com.macstadium.orka;

import com.intellij.openapi.diagnostic.Logger;
import com.macstadium.orka.client.DeletionResponse;
import com.macstadium.orka.client.VMResponse;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import jetbrains.buildServer.clouds.CloudImage;
import jetbrains.buildServer.log.Loggers;

public class RemoveFailedInstancesTask implements Runnable {
    private static final Logger LOG = Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT + OrkaConstants.TYPE);
    private OrkaCloudClient client;

    public RemoveFailedInstancesTask(OrkaCloudClient client) {
        this.client = client;
    }

    @Override
    public void run() {
        LOG.debug("Running remove failed instances...");
        this.client.getImages().forEach(image -> this.terminateFailedInstances(image));
        LOG.debug("Failed instances task completed.");
    }

    private void terminateFailedInstances(CloudImage image) {
        List<OrkaCloudInstance> instancesToTerminate = image.getInstances().stream()
                .map(instance -> (OrkaCloudInstance) instance).filter(instance -> instance.isMarkedForTermination())
                .collect(Collectors.toList());

        if (instancesToTerminate.size() > 0) {
            LOG.debug(String.format("Failed instances found for image %s", image.getName()));

            instancesToTerminate.forEach(instance -> {
                LOG.debug(String.format("Removing instance with id: %s", instance.getInstanceId()));
                VMResponse vmResponse;
                try {
                    vmResponse = this.client.getVM(instance.getInstanceId(), instance.getNamespace());

                    if (vmResponse.isSuccessful()) {
                        this.tryDeleteVM(instance);
                    } else {
                        this.terminateInstance(instance);
                    }
                } catch (IOException e) {
                    LOG.info(String.format("Failed to get VM: %s", instance.getInstanceId()), e);
                }
            });
        }
    }

    private void tryDeleteVM(OrkaCloudInstance instance) {
        try {
            DeletionResponse response = this.client.deleteVM(instance.getInstanceId(), instance.getNamespace());
            if (response.isSuccessful()) {
                this.terminateInstance(instance);
            } else {
                LOG.info(String.format("Failed to terminate VM: %s and message: %s", instance.getInstanceId(),
                        response.getHttpResponse()));
            }
        } catch (IOException e) {
            LOG.info(String.format("Failed to terminate VM: %s", instance.getInstanceId()), e);
        }
    }

    private void terminateInstance(OrkaCloudInstance failedInstance) {
        failedInstance.getImage().terminateInstance(failedInstance.getInstanceId());
    }
}
