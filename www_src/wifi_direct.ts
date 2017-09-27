declare var exec;
declare var module;

class WifiDirect {

  private _Instance: WifiDirectNode;

  public getInstance(type, domain, name, port = 32051, props, success, failure) {
    return exec(hash => {
      if (!this._Instance || this._Instance.hash !== hash) {
        this._Instance = new WifiDirectNode(type, domain, name, port, props, hash);
      }
      success(this._Instance);
    }, failure, 'WifiDirect', 'getInstance', [type, domain, name, port, props]);
  }
}

class WifiDirectNode {

  constructor(public type, public domain, public name, public port, public txtRecords, public hash) {
  }

  public startDiscovering(success, failure) {
    return exec(success, failure, 'WifiDirect', 'startDiscovering', []);
  }

  public stopDiscovering(success, failure) {
    return exec(success, failure, 'WifiDirect', 'stopDiscovering', []);
  }

  public connect(peer, success, failure) {
    return exec(success, failure, 'WifiDirect', 'connect', [peer]);
  }

  public disconnect(success, failure) {
    return exec(success, failure, 'WifiDirect', 'disconnect', []);
  }

  public shutdown(success, failure) {
    return exec(success, failure, 'WifiDirect', 'shutdown', []);
  }
}

module.exports = new WifiDirect();