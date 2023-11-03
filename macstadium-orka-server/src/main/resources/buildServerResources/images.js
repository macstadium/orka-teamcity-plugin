function OrkaImagesViewModel(BS, $F, ko, $, config) {
  var self = this;

  self.tokenInitialized;
  self.namespaceInitialized;

  self.loadingVms = ko.observable(false);
  self.loadingAgentPools = ko.observable(false);

  self.orkaEndpoint = ko.observable().extend({ throttle: 300, required: true });
  self.token = ko.observable().extend({ throttle: 300, required: true });
  self.tokenEncrypted = ko.observable();
  self.namespace = ko.observable().extend({ throttle: 300, required: true });

  self.namespace.subscribe(function (data) {
    if (!self.namespaceInitialized && !data) {
      self.namespaceInitialized = true;
      self.namespace("orka-default");
    }
  });

  self.token.subscribe(function (data) {
    if (self.tokenInitialized) {
      self.tokenEncrypted(BS.Encrypt.encryptData(data, $F("publicKey")));
    } else {
      self.tokenInitialized = true;
    }
  });

  self.orkaEndpoint.subscribe(function () {
    self.loadInfo();
  });

  self.tokenEncrypted.subscribe(function () {
    self.loadInfo();
  });

  self.vms = ko.observableArray([]);

  self.vmName = ko.observable().extend({ required: true });
  self.currentVm = ko.observable().extend({ required: true });
  self.vmUser = ko.observable().extend({ required: true });
  self.vmPassword = ko.observable().extend({ required: true });

  self.agentPools = ko.observableArray([]);
  self.agentPoolId = ko.observable().extend({ required: true });
  self.currentAgentPoolId = ko.observable();

  self.agentDirectory = ko.observable().extend({ required: true });

  self.showMappings = ko.observable(false);
  self.initialNodeMappings = ko.observable();
  self.nodeMappings = ko.observable();

  self.initialNodeMappings.subscribe(function (data) {
    if (data) {
      self.nodeMappings(data.trim());
    }
  });

  self.loadInfo = function () {
    self.loadingVms(true);

    var credentials = getCredentials();
    if (credentials) {
      var url = config.baseUrl + "?resource=vms";
      $.post(url, credentials)
        .then(function (response) {
          var $response = $(response);

          self.vms(getVms($response));
          self.currentVm(self.vmName());
        })
        .always(function () {
          self.loadingVms(false);
        });
    }
  };

  function getCredentials() {
    if (!self.orkaEndpoint() || !self.tokenEncrypted()) {
      return null;
    }

    return {
      orkaEndpoint: self.orkaEndpoint(),
      token: self.tokenEncrypted(),
    };
  }

  function getVms($response) {
    return $response
      .find("vms:eq(0) vm")
      .map(function () {
        return $(this).text();
      })
      .get();
  }

  (function loadAgentPools() {
    self.loadingAgentPools(true);
    var url =
      config.baseUrl +
      "?resource=agentPools&projectId=" +
      encodeURIComponent(config.projectId);
    return $.post(url)
      .then(function (response) {
        var $response = $(response);
        var agentPools = $response
          .find("agentPools:eq(0) agentPool")
          .map(function () {
            return {
              id: $(this).attr("id"),
              text: $(this).text(),
            };
          })
          .get();

        self.agentPools(agentPools);
        self.currentAgentPoolId(self.agentPoolId());
      })
      .always(function () {
        self.loadingAgentPools(false);
      });
  })();
}
