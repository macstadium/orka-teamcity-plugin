# Orka by MacStadium TeamCity Plugin Setup

This guide explains how to set up the Orka TeamCity plugin.

The plugin allows you to configure TeamCity, so it can spin up on demand ephemeral agents that run in an Orka environment.

## Requirements

- [Orka][orka] VM configuration

## Setup overview

1. [Install](#set-up-an-orka-vm-base-image) the Orka TeamCity plugin.
2. Set up an Orka VM [base image](#set-up-an-orka-vm-base-image). The image must have SSH enabled.
3. Set up an Orka [VM configuration](#set-up-an-orka-vm-config-for-the-ephemeral-agents) using the base image from **Step 2**. The Orka VM config is the container template that the plugin will use to spin up ephemeral Mac machines.
4. Set up а TeamCity [cloud profile](usage.md#configure-the-macstadium-orka-plugin).

## Install the Orka TeamCity Plugin

In your TeamCity server:

1. Go to `Administration` -> `Server Administration` -> `Plugins List` and click `Upload plugin zip`.
2. Choose the `.zip` archive containing the plugin and click `Upload plugin zip`.
3. [Enable][enable-plugin] the plugin.

## Set up an Orka VM base image

The Orka VM base image is used when the Orka plugin spins up ephemeral TeamCity agents.

1. Set up a new Orka VM. You can set up an Orka VM using the Orka [CLI][cli] or [REST API][api]. For more information, see the Orka [quick start guide][quick-start].
2. Connect to the Orka VM using SSH or VNC.
3. Verify the TeamCity build agent is installed. For more information, see [here][build-agent-install].
   **Note** Make sure to note down the installation directory for the build agent. You will need it later to configure a [TeamCity cloud profile](usage.md#configure-the-macstadium-orka-plugin).
4. Start the agent manually, by running `<installation path>/bin/agent.sh start` on the VM.
5. Verify the build agent is up to date. This ensures faster startup of the build agent. To do that:
   - In your TeamCity server go to `Agents` -> `Pools`.
   - Click on the newly connected agent.
   - Verify its status is `Connected`. If an upgrade is still in progress you will see `(Agent has unregistered (will upgrade))`.
6. Stop the agent manually, by running `<installation path>/bin/agent.sh stop kill` on the VM.
7. Verify that SSH login with a password is enabled. SSH login is used by the Orka plugin to manage the Orka VM.
8. Restart the VM. This ensures that all changes are persistent.
9. Create a new base image from the configured VM. You can do it with the Orka [CLI][cli-save-image] or the [REST API][rest-save-image].

## Set up an Orka VM configuration for the ephemeral agents

To allow the Orka plugin to spin up ephemeral VMs in Orka, create an Orka VM config (a container template) that uses the SSH-enabled base image you just created.

You can create an Orka VM config using the Orka [CLI][cli] or [REST API][api]. For more information, see the Orka [quick start guide][quick-start].

## Connectivity

The communication between the plugin and the agent is bidirectional.

This means your Orka environment must have visibility to the TeamCity server and vice versa.

## Usage

How to use the Orka plugin, see [here](usage.md).

[enable-plugin]: https://www.jetbrains.com/help/teamcity/installing-additional-plugins.html#InstallingAdditionalPlugins-Enablingtheplugin
[build-agent-install]: https://www.jetbrains.com/help/teamcity/setting-up-and-running-additional-build-agents.html#SettingupandRunningAdditionalBuildAgents-InstallingviaZIPFile
[orka]: https://orkadocs.macstadium.com/docs/getting-started
[cli]: https://orkadocs.macstadium.com/docs/example-cli-workflows
[api]: https://documenter.getpostman.com/view/6574930/S1ETRGzt?version=latest
[quick-start]: https://orkadocs.macstadium.com/docs/quick-start
[cli-save-image]: https://orkadocs.macstadium.com/docs/existing-images-upload-management#section--create-or-update-a-base-image-from-a-deployed-vm-
[rest-save-image]: https://documenter.getpostman.com/view/6574930/S1ETRGzt?version=latest#56c72702-c1cd-44e9-888e-0b3625dc22e4
