'use strict';
var exec = require('cordova/exec');
var WifiDirect = /** @class */ (function () {
    function WifiDirect() {
    }
    WifiDirect.prototype.getInstance = function (type, domain, name, port, props, success, failure) {
        var _this = this;
        if (port === void 0) { port = 32051; }
        return exec(function (hash) {
            if (!_this._Instance || _this._Instance.hash !== hash) {
                _this._Instance = new WifiDirectNode(type, domain, name, port, props, hash);
            }
            success(_this._Instance);
        }, failure, 'WifiDirect', 'getInstance', [type, domain, name, port, props]);
    };
    return WifiDirect;
}());
var WifiDirectNode = /** @class */ (function () {
    function WifiDirectNode(type, domain, name, port, txtRecords, hash) {
        this.type = type;
        this.domain = domain;
        this.name = name;
        this.port = port;
        this.txtRecords = txtRecords;
        this.hash = hash;
    }
    WifiDirectNode.prototype.startDiscovering = function (success, failure) {
        return exec(success, failure, 'WifiDirect', 'startDiscovering', []);
    };
    WifiDirectNode.prototype.stopDiscovering = function (success, failure) {
        return exec(success, failure, 'WifiDirect', 'stopDiscovering', []);
    };
    WifiDirectNode.prototype.connect = function (peer, success, failure) {
        return exec(success, failure, 'WifiDirect', 'connect', [peer]);
    };
    WifiDirectNode.prototype.disconnect = function (success, failure) {
        return exec(success, failure, 'WifiDirect', 'disconnect', []);
    };
    WifiDirectNode.prototype.shutdown = function (success, failure) {
        return exec(success, failure, 'WifiDirect', 'shutdown', []);
    };
    return WifiDirectNode;
}());
module.exports = new WifiDirect();
