function OrkaImagesViewModel(BS, $F, ko, $, config) {
  var self = this;

  self.orkaPasswordInitialized;

  self.loadingVms = ko.observable(false);
  self.loadingAgentPools = ko.observable(false);

  self.orkaEndpoint = ko.observable().extend({ throttle: 300, required: true });
  self.orkaUser = ko.observable().extend({ throttle: 300, required: true });
  self.orkaPassword = ko.observable().extend({ throttle: 300, required: true });
  self.orkaPasswordEncrypted = ko.observable();

  self.orkaPassword.subscribe(function(data) {
    if (self.orkaPasswordInitialized) {
      self.orkaPasswordEncrypted(BS.Encrypt.encryptData(data, $F("publicKey")));
    } else {
      self.orkaPasswordInitialized = true;
    }
  });

  self.orkaEndpoint.subscribe(function() {
    self.loadInfo();
  });

  self.orkaUser.subscribe(function() {
    self.loadInfo();
  });

  self.orkaPasswordEncrypted.subscribe(function(daa) {
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

  self.loadInfo = function() {
    self.loadingVms(true);

    var credentials = getCredentials();
    if (credentials) {
      var url = config.baseUrl + "?resource=vms";
      $.post(url, credentials)
        .then(function(response) {
          var $response = $(response);

          self.vms(getVms($response));
          self.currentVm(self.vmName());
        })
        .always(function() {
          self.loadingVms(false);
        });
    }
  };

  function getCredentials() {
    if (
      !self.orkaEndpoint() ||
      !self.orkaUser() ||
      !self.orkaPasswordEncrypted()
    ) {
      return null;
    }

    return {
      orkaEndpoint: self.orkaEndpoint(),
      orkaUser: self.orkaUser(),
      orkaPassword: self.orkaPasswordEncrypted()
    };
  }

  function getVms($response) {
    return $response
      .find("vms:eq(0) vm")
      .map(function() {
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
      .then(function(response) {
        var $response = $(response);
        var agentPools = $response
          .find("agentPools:eq(0) agentPool")
          .map(function() {
            return {
              id: $(this).attr("id"),
              text: $(this).text()
            };
          })
          .get();

        self.agentPools(agentPools);
        self.currentAgentPoolId(self.agentPoolId());
      })
      .always(function() {
        self.loadingAgentPools(false);
      });
  })();
}
