package com.macstadium.orka;

import com.intellij.openapi.diagnostic.Logger;
import com.macstadium.orka.client.DeletionResponse;
import com.macstadium.orka.client.VMResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
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
        this.client.getImages().forEach(image -> this.terminateFailedInstances(image));
    }

    private void terminateFailedInstances(CloudImage image) {
        List<OrkaCloudInstance> instancesToTerminate = image.getInstances().stream()
                .map(instance -> (OrkaCloudInstance) instance).filter(instance -> instance.isMarkedForTermination())
                .collect(Collectors.toList());

        if (instancesToTerminate.size() > 0) {
            try {
                Set<String> runningInstances = this.getRunningInstances(image.getName());
                instancesToTerminate.forEach(instance -> {
                    if (runningInstances.contains(instance.getInstanceId())) {
                        this.tryDeleteVM(instance);
                    } else {
                        this.terminateInstance(instance);
                    }
                });

            } catch (IOException e) {
                LOG.info(String.format("Failed to execute terminate failed instances for image: %s", image.getName()),
                        e);
            }
        }
    }

    private Set<String> getRunningInstances(String imageName) throws IOException {
        VMResponse vmResponse = this.client.getVM(imageName);
        return Arrays.stream(vmResponse.getInstances()).map(instance -> instance.getId()).collect(Collectors.toSet());
    }

    private void tryDeleteVM(OrkaCloudInstance instance) {
        try {
            DeletionResponse response = this.client.deleteVM(instance.getInstanceId());
            if (!response.hasErrors()) {
                this.terminateInstance(instance);
            } else {
                LOG.info(String.format("Failed to terminate VM: %s and message: %s", 
                    instance.getInstanceId(), Arrays.toString(response.getErrors())));
            }
        } catch (IOException e) {
            LOG.info(String.format("Failed to terminate VM: %s", instance.getInstanceId()), e);
        }
    }

    private void terminateInstance(OrkaCloudInstance failedInstance) {
        failedInstance.getImage().terminateInstance(failedInstance.getInstanceId());
    }
}