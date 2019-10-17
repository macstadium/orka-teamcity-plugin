# Orka by MacStadium TeamCity Plugin Usage

This guide explains how to use the Orka TeamCity plugin.

The plugin allows you to configure TeamCity, so it can spin up on demand ephemeral agents that run in an Orka environment.

How to set up the plugin, see [here](setup.md).

## Configure the Orka TeamCity plugin

The plugin allows you to configure a cloud profile per project.

In your TeamCity server:

1. Go to `Projects` and click the project you want to configure with a cloud profile.
2. Click `Edit Project Settings`.
3. Go to `Cloud Profiles` and click `Create new profile`.
4. Specify a name and select `Orka Cloud` for the `Cloud type` drop-down menu.
5. Specify the required parameters:
   - `Orka endpoint` - The Orka endpoint. For more information, see your Orka [IP Plan][ip-plan]
   - `Orka user email` - User used to connect to the Orka environment. Created using the [CLI][cli-create-user] or the [REST API][rest-create-user]
   - `Orka password` - Password used to connect to the Orka environment. Created using the [CLI][cli-create-user] or the [REST API][rest-create-user]
   - `VM template` - The name of the VM configuration you created [here](setup.md#set-up-an-orka-vm-base-image)
   - `VM user` - User used to SSH to the VM
   - `VM SSH password` - User password used to SSH to the VM
   - `Maximum instances count` - Maximum amount of instances that can be created
   - `Agent Pool` - The TeamCity agent pool which will be used to create new agents
   - `Agent directory` - The installation directory of the agent on the VM. For instance `/Users/admin/BuildAgent/`
6. Click `Create`.

**NOTE** `Agent Push` is not supported.

## Using the Orka TeamCity plugin

Once the cloud profile is successfully configured, TeamCity will automatically spin up and destroy agents in the respective orka environment.

[ip-plan]: https://orkadocs.macstadium.com/docs/orka-glossary#section-ip-plan
[cli-create-user]: https://orkadocs.macstadium.com/docs/quick-start#section--setting-up-a-user-
[rest-create-user]: https://documenter.getpostman.com/view/6574930/S1ETRGzt?version=latest#55dcdc47-c542-4e85-88f4-e5b2c1734d50