<%@ page import="com.macstadium.orka.OrkaConstants" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="/include.jsp" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
</table>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean" />
<jsp:useBean id="basePath" class="java.lang.String" scope="request"/>
<jsp:useBean id="constants" class="com.macstadium.orka.OrkaConstants"/>

<script type="text/javascript">
    BS.LoadStyleSheetDynamically("<c:url value='${resPath}settings.css'/>");
</script>

<div id="orka-setting">
    <h2 class="noBorder section-header">Orka Config</h2>
    <table class="runnerFormTable">
        <tr>
            <th><label for="${constants.orkaEndpoint}">Orka endpoint: <l:star/></label></th>
            <td>
                <input type="text" name="prop:${constants.orkaEndpoint}" id="${constants.orkaEndpoint}" class="longField" value="<c:out value="${propertiesBean.properties[constants.orkaEndpoint]}"/>" data-bind="initValue: orkaEndpoint, textInput: orkaEndpoint"/>
                <span class="error option-error" data-bind="validationMessage: orkaEndpoint"></span>
                <span class="smallNote">
                    Specify Orka endpoint. You can find it in your IP Plan.<bs:help
                    urlPrefix="https://docs.macstadium.com/docs/ip-plan" file=""/>
                </span>
            </td>
        </tr>

        <tr>
            <th><label for="${constants.token}">Orka Token: <l:star/></label></th>
            <td>
                <props:passwordProperty name="${constants.token}" className="settings longField"/>
                <span class="error option-error" data-bind="validationMessage: token"></span>
                <span class="smallNote">
                    Service account token used to access Orka.
                </span>
            </td>
        </tr>
    </table>
    <h2 class="noBorder section-header">Orka VM Config</h2>
    <table class="runnerFormTable">
        <tr>
            <th><label for="${constants.vmName}">VM Config: <l:star/></label></th>
            <td>
                <select name="prop:${constants.vmName}" class="longField ignoreModified" data-bind="options: vms, value: currentVm"></select>
                <input type="hidden" class="longField" value="<c:out value="${propertiesBean.properties[constants.vmName]}"/>" data-bind="initValue: vmName, value: currentVm"/>
                <span data-bind="css: {invisible: !loadingVms()}">
                        <i class="icon-refresh icon-spin"></i>
                </span>
                <span class="smallNote">
                    Specify the VM config used to create new Orka machines.
                </span>
            </td>
        </tr>

        <tr>
            <th><label for="${constants.namespace}">Namespace: <l:star/></label></th>
            <td>
                <input type="text" name="prop:${constants.namespace}" id="${constants.namespace}" class="longField" value="<c:out value="${propertiesBean.properties[constants.namespace]}"/>" data-bind="initValue: namespace, textInput: namespace"/>
                <span class="error option-error" data-bind="validationMessage: namespace"></span>
                <span class="smallNote">
                    The namespace used to deploy VMs to.
                </span>
            </td>
        </tr>

        <tr>
            <th><label for="${constants.vmUser}">VM user: <l:star/></label></th>
            <td>
                <input type="text" name="prop:${constants.vmUser}" id="${constants.vmUser}" class="longField" value="<c:out value="${propertiesBean.properties[constants.vmUser]}"/>" data-bind="initValue: vmUser, textInput: vmUser"/>
                <span class="error option-error" data-bind="validationMessage: vmUser"></span>
                <span class="smallNote">
                    Specify the user used to SSH to the Orka VM.
                </span>
            </td>
        </tr>

        <tr>
            <th><label for="${constants.vmPassword}">VM SSH password: <l:star/></label></th>
            <td>
                <div>
                    <props:passwordProperty name="${constants.vmPassword}" className="settings longField"/>
                    <span class="error option-error" data-bind="validationMessage: vmPassword"></span>
                    <span class="smallNote">
                        Specify the SSH password for the VM user.
                    </span>
                </div>
            </td>
        </tr>
    </table>
    <h2 class="noBorder section-header">Advanced Settings</h2>
    <table class="runnerFormTable">
        <tr class="advancedSetting">
            <th><label for="${constants.instanceLimit}">Maximum instances count:</label></th>
            <td>
                <props:textProperty name="${constants.instanceLimit}" className="settings"/>
                <span class="smallNote">Maximum number of instances that can be started. Use blank to have no limit.</span>
            </td>
        </tr>

        <tr class="advancedSetting">
            <th><label for="${constants.agentPoolId}">Agent pool:</label></th>
            <td>
                <select name="prop:${constants.agentPoolId}" class="longField ignoreModified"
                        data-bind="options: agentPools, optionsText: 'text', optionsValue: 'id', value: currentAgentPoolId"></select>
                <input type="hidden" class="longField" value="<c:out value="${propertiesBean.properties[constants.agentPoolId]}"/>" data-bind="initValue: agentPoolId, value: currentAgentPoolId"/>
                <span data-bind="css: {invisible: !loadingAgentPools()}">
                    <i class="icon-refresh icon-spin"></i>
                </span>
            </td>
        </tr>

        <tr class="advancedSetting">
            <th><label for="${constants.agentDirectory}">Agent directory:</label></th>
            <td>
                <input type="text" name="prop:${constants.agentDirectory}" id="${constants.agentDirectory}" class="longField" value="<c:out value="${propertiesBean.properties[constants.agentDirectory]}"/>" data-bind="initValue: agentDirectory, textInput: agentDirectory"/>
                <span class="smallNote">
                    Specify the installation directory of the TeamCity agent on the Orka VM.
                </span>
            </td>
        </tr>

        <tr class="advancedSetting">
            <th><label for="${constants.agentDirectory}">Node mappings:</label></th>
            <td>
                <div data-bind="visible: showMappings" style="display: none">
                    <div>Edit Node Mappings:</div>
                    <textarea name="prop:${constants.nodeMappings}" class="mappings"
                              rows="5" cols="30"
                              data-bind="initValue: initialNodeMappings, textInput: nodeMappings">
                        <c:out value="${propertiesBean.properties[constants.nodeMappings]}"/>
                    </textarea>
                </div>
                <a href="#" data-bind="click: function() { showMappings(true) }, visible: !showMappings()">Node Mappings</a>
                <span class="smallNote">Overwrite the default host address used to connect to an Orka VM.</span>
                <span class="smallNote">Mappings are in "PRIVATE_HOST;PUBLIC_HOST" format, separated by a new line.</span>
            </td>
        </tr>
    </table>
</div>

<script type="text/javascript">
    function setBindings(id, binding) {
        var element = document.getElementById(id);
        element.setAttribute("data-bind", binding)
    }

    $j.when($j.getScript("<c:url value="${resPath}knockout-3.4.0.js"/>").then(function () {
            return $j.when($j.getScript("<c:url value="${resPath}knockout.validation-2.0.3.js"/>"),
                $j.getScript("<c:url value="${resPath}knockout.extend.js"/>"));
        }),
        $j.getScript("<c:url value="${resPath}images.js"/>")
    ).then(function () {
        setBindings("secure:cloud.orka.token", "initValue: token, textInput: token");
        setBindings("prop:encrypted:secure:cloud.orka.token", "initValue: tokenEncrypted, textInput: tokenEncrypted");
        setBindings("secure:cloud.orka.vm.password", "initValue: vmPassword, textInput: vmPassword");

        ko.validation.init({insertMessages: false});
        ko.applyBindings(new OrkaImagesViewModel(BS, $F, ko, $j, {
            baseUrl: "<c:url value='${basePath}'/>",
            projectId: "${projectId}"
        }));
    });
</script>

<table class="runnerFormTable">
