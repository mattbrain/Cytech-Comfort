// Node Cytech Comfort UPNP Interface
// Author: Matt Brain (matt.brain@gmail.com)
// Version 0.0.1 Unstable
// Please see https://github.com/mattbrain/Cytech-Comfort for more information and latest release


var net = require('net');
var upnp = require("peer-upnp");
var makerkey = "QcxwKyqUzksZmhpBHCJjf";
var fs = require('fs');
var path = require('path');
var xml2js = require('xml2js');
var util = require('util');
var prop = require('properties-parser')



// HTTP required for UPNP and IFTTT
var http = require('http');

// UPNP server
//var server = http.createServer();
//var peer = null;
//var device = null;
var portstart = 10000
//configureUPNP();

// Configure Web Interface
var express = require('express');


// Read Configuration
var configFile = './alarm.config';
var config = prop.read(configFile)

var enableDebug = false;

if (config["debug"]==="true") {
	enableDebug = true;
	debug ("Debugging is enabled, starting up")
}

if (config["http_interface"]==="true") {
	var AlarmHTTP = express();
	configureHTTP();
}

if (config["config"]) {
	var alarmConfig = new ComfortConfiguration(config["config"]);
} else {
	debug("Unable to find config file entry in configuration file " + configFile)
}




// Start Processes
var alarm   = new Comfort();
alarm.connect(config["port"],config["host"],config["user"]);

// Comfort Objects
function Zone(zone, zonedata) {
	var active = true;

	var _DateUpdated = Date.now();
	var _device = new UPNPService("Zone",zone, zone, active, zonedata.Name);
	var thisZone = this;	
	var _service = _device.createService({
		domain: "www.cytech.com",
		type: "zone",
		serviceId: "zone", 
		version: "1",
		implementation: {
			GetZone: function(inputs){
				return {RetZoneValue: this.get("Zone")}
			},
			GetMaker: function(inputs){
				return {RetMakerValue: this.get("Maker")}
			},
			GetStatus: function(inputs){
				return {RetStatusValue: this.get("Status")}
			},
			GetActive: function(inputs){
				return {RetActiveValue: this.get("Active")}
			},
			GetAnalogue: function(inputs) {
				thisZone.pollAnalogue()
				return {RetAnalogueValue: this.get("Analogue")}					
			},
			GetHealth: function(inputs) {
				thisZone.pollAnalogue()
				return {RetHealthValue: this.get("Health")}
			},
			GetBypass: function(inputs) {
				thisZone.pollBypass()
				return {RetBypassValue: this.get("Bypass")}					
			},
			GetZoneTypeName: function(inputs) {
				return {RetZoneTypeNameValue: this.get("ZoneTypeName")}
			},
			GetEntryPath: function(inputs) {
				return {RetEntryPathValue: this.get("EntryPath")}
			},
			SetBypass: function(inputs) {
				thisZone.requestBypass(inputs.NewBypassValue);
			},
			SetMaker: function(inputs){
				this.set("Maker", inputs.NewMakerValue);
				this.notify("Maker");
			}

		},
		description: {
			actions: {
				GetZone: {
					outputs: {
						RetZoneValue: "Zone"
					}
				},
				GetMaker: {
					outputs: {
						RetMakerValue: "Maker"
					}
				},
				GetStatus: {
					outputs: {
						RetStatusValue: "Status"
					}
				},
				GetActive: {
					outputs: {
						RetActiveValue: "Active"
					}
				},
				GetAnalogue: {
					outputs: {
						RetAnalogueValue: "Analogue"
					}
				},
				GetHealth: {
					outputs: {
						RetHealthValue: "Health"
					}
				},
				GetBypass: {
					outputs: {
						RetBypassValue: "Bypass"
					}
				},
				GetZoneTypeName: {
					outputs: {
						RetZoneTypeNameValue: "ZoneTypeName"
					}
				},
				GetEntryPath: {
					outputs: {
						RetEntryPath: "EntryPath"
					}
				},
				SetBypass: {
					inputs: {
						NewBypassValue: "Bypass"
					}
				},				
				SetMaker: {
					inputs: {
						NewMakerValue: "Maker"
					}
				}	
			},	
			variables: {Zone: "int", Status: "boolean", Maker: "boolean", Active: "boolean", Analogue: "int", Bypass: "boolean", Health: "string", ZoneTypeName: "string", EntryPath: "boolean" }	
		}	 
	});
	_service.set("Status",0);
	_service.set("Zone",zone);
	_service.set("Active",false);
	_service.set("Maker",0);
	_service.set("Analogue",0);
	_service.set("Bypass",0);
	_service.set("Health","Healthy");
	_service.set("ZoneTypeName",zonedata.ZoneTypeName)
	_service.set("EntryPath", (zonedata.EntryPath=="true"));

	this.pollAnalogue = function pollAnalogue() {
		var _zone = toHexByte(zone);
		var commandString = "A?" + _zone;
		alarm.sendCommand(commandString);
	}

	this.pollBypass = function pollBypass() {
		var _zone = toHexByte(zone);
		var commandString = "B?" + _zone;
		alarm.sendCommand(commandString);	
	}

	this.requestBypass = function requestBypass(value) {
		var _zone = toHexByte(zone);
		if (value=="1") {
			var commandString = "DA4B" + _zone;
		} else {
			var commandString = "DA4C" + _zone;
		}
		alarm.sendCommand(commandString);
	}

	this.setBypass = function setBypass(value) {
		_service.set("Bypass", value);
		_service.notify("Bypass");
	}
 
	this.setAnalogue = function setAnalogue(value) {
		_service.set("Analogue", value);
		_service.notify("Analogue");
		if (value < 64 ) {
			_service.set("Health", "Short Circuit")
		} else if (value > 191) {
			_service.set("Health", "Open Circuit")
		} else {
			_service.set("Health", "Healthy")
		}
		_service.notify("Health")
	}

	this.getZone = function getZone() {
		return _service.get("Zone");
	}
	
	this.setZone = function setZone(Zone) {
		_service.set("Zone",Zone);
	}
	
	this.getState = function getState() {
		return _service.get("Status");
	}
	
	this.setStateSilent = function setStateSilent(State) {
		_service.set("Status", State);
		_service.set("Active", true);
		_service.notify("Status");
		_DateUpdated = Date.now();
		debug("Setting Zone " + _service.get("Zone") + " to " + _service.get("Status") + " silently");
	}
		
	this.setState = function setState(State) {
		_service.set("Status", State);
		_service.set("Active", true);
		_service.notify("Status");
		_DateUpdated = Date.now();
		debug("Setting Zone " + _service.get("Zone") + " to " + _service.get("Status"));
		debug("Maker = " + _service.get("Maker"));
		if (_service.get("Maker")===1) {
			debug("")
			sendIFTTT("Zone",_service,get("Zone"),new Date(_DateUpdated).toISOString());
		}
	}
	
	this.getDateUpdated = function getDateUpdated () {
		return _DateUpdated;
	}
	
	this.setMaker = function setMaker(maker) {
		if (maker===true) {
			_service.set("Maker",1);
		} else {
			_service.set("Maker",0);
		}
	}
	
	this.getMaker = function getMaker() {
		return _service.get("Maker");
	}
}
function Output(zone, zonedata) {
	
	var active=true;
	var thisZone = this;
	var _DateUpdated = Date.now();
	var _device = new UPNPService("Output",zone, zone+100, active, zonedata.Name);	
	var _service = _device.createService({
		domain: "www.cytech.com",
		type: "output",
		serviceId: "output", 
		version: "1",
		implementation: {
			GetZone: function(inputs){
				return {RetZoneValue: this.get("Zone")}
			},
			GetMaker: function(inputs){
				return {RetMakerValue: this.get("Maker")}
			},
			GetStatus: function(inputs){
				return {RetStatusValue: this.get("Status")}
			},
			GetActive: function(inputs){
				return {RetActiveValue: this.get("Active")}
			},
			SetMaker: function(inputs){
				debug("Maker Value Set");
				this.set("Maker", inputs.NewMakerValue);
				this.notify("Maker");
				
			},
			SetStatus: function(inputs){
				debug("Status Value Set");
				thisZone.requestState(inputs.NewStatusValue);
			}

		},
		description: {
			actions: {
				GetZone: {
					outputs: {
						RetZoneValue: "Zone"
					}
				},
				GetMaker: {
					outputs: {
						RetMakerValue: "Maker"
					}
				},
				GetStatus: {
					outputs: {
						RetStatusValue: "Status"
					}
				},
				GetActive: {
					outputs: {
						RetActiveValue: "Active"
					}
				},				
				SetMaker: {
					inputs: {
						NewMakerValue: "Maker"
					}
				},
				SetStatus: {
					inputs: {
						NewStatusValue: "Status"
					}
				}	
			},	
			variables: {Zone: "int", Status: "boolean", Maker: "boolean", Active: "boolean", }	
		}	 
	});
	debug("Setting up output zone:" + zone);
	_service.set("Status",0);
	_service.set("Zone",zone);
	_service.set("Active",false);
	_service.set("Maker",0);

	this.getZone = function getZone() {
		return _service.get("Zone");
	}
	
	this.setZone = function setZone(Zone) {
		_service.set("Zone",Zone);
	}
	
	this.getState = function getState() {
		return _service.get("Status");
	}

	this.requestState = function requestState(state){
		var _zone = toHexByte(zone)
		debug ("->this.requestState:" + state + "for zone:" + _zone);
		var commandString = "O!" + _zone + "0" + state;
		debug("Sending " + commandString + "to alarm");
		alarm.sendCommand(commandString);
	}

	this.setStateSilent = function setStateSilent(State) {
		_service.set("Status", State);
		_service.set("Active", true);
		_service.notify("Status");
		_DateUpdated = Date.now();
		debug("Setting Output " + _service.get("Zone") + " to " + _service.get("Status") + " silently");
	}
		
	this.setState = function setState(State) {
		_service.set("Status", State);
		_service.set("Active", true);
		_service.notify("Status");
		_DateUpdated = Date.now();
		debug("Setting Output " + _service.get("Zone") + " to " + _service.get("Status"));
		debug("Maker = " + _service.get("Maker"));
		if (_service.get("Maker")===1) {
			debug("")
			sendIFTTT("Output",_service,get("Zone"),new Date(_DateUpdated).toISOString());
		}
	}
	
	this.getDateUpdated = function getDateUpdated () {
		return _DateUpdated;
	}
	
	this.setMaker = function setMaker(maker) {
		if (maker===true) {
			_service.set("Maker",1);
		} else {
			_service.set("Maker",0);
		}
	}
	
	this.getMaker = function getMaker() {
		return _service.get("Maker");
	}

	//this.requestState = function requestState(state) {
	//	debug("Requesting "+ state + " on zone " + getZone());
		//				var commandString = "O!" + this.get("Zone").toInt().toString(16) + "0" + inputs.NewStatusValue;
		//				debug("Sending " + commandString + "to alarm");
		//				alarm.sendCommand(commandString);

	//}
}
function Counter(counter, counterdata) {
	var active=true;
	var thisCounter = this;
	var _DateUpdated = Date.now();
	var _device = new UPNPService("Counter",counter, counter+400, active, counterdata.Name);	
	var _service = _device.createService({
		domain: "www.cytech.com",
		type: "counter",
		serviceId: "counter", 
		version: "1",
		implementation: {
			GetCounter: function(inputs){
				return {RetCounterValue: this.get("Counter")}
			},
			GetMaker: function(inputs){
				return {RetMakerValue: this.get("Maker")}
			},
			GetStatus: function(inputs){
				return {RetStatusValue: this.get("Status")}
			},
			GetActive: function(inputs){
				return {RetActiveValue: this.get("Active")}
			},
			SetMaker: function(inputs){
				debug("Maker Value Set");
				this.set("Maker", inputs.NewMakerValue);
				this.notify("Maker");
				
			},
			SetStatus: function(inputs){
				debug("Status Value Set");
				thisCounter.requestState(inputs.NewStatusValue);
			}

		},
		description: {
			actions: {
				GetCounter: {
					outputs: {
						RetCounterValue: "Counter"
					}
				},
				GetMaker: {
					outputs: {
						RetMakerValue: "Maker"
					}
				},
				GetStatus: {
					outputs: {
						RetStatusValue: "Status"
					}
				},
				GetActive: {
					outputs: {
						RetActiveValue: "Active"
					}
				},				
				SetMaker: {
					inputs: {
						NewMakerValue: "Maker"
					}
				},
				SetStatus: {
					inputs: {
						NewStatusValue: "Status"
					}
				}	
			},	
			variables: {Counter: "int", Status: "boolean", Maker: "boolean", Active: "boolean", }	
		}	 
	});
	debug("Setting up counter:" + counter);
	_service.set("Status",0);
	_service.set("Counter",counter);
	_service.set("Active",false);
	_service.set("Maker",0);

	this.getCounter = function getCounter() {
		return _service.get("Counter");
	}
	
	this.setCounter = function setCounter(counter) {
		_service.set("Counter",counter);
	}
	
	this.getState = function getState() {
		return _service.get("Status");
	}

	this.requestState = function requestState(state){
		var _counter = toHexByte(counter)
		var _state = toHexByte(parseInt(state))
		debug ("->this.requestState:" + state + "for counter:" + _counter);
		var commandString = "C!" + _counter + _state;
		debug("Sending " + commandString + "to alarm");
		alarm.sendCommand(commandString);
	}

	this.setStateSilent = function setStateSilent(State) {
		_service.set("Status", State);
		_service.set("Active", true);
		_service.notify("Status");
		_DateUpdated = Date.now();
		debug("Setting Counter " + _service.get("Counter") + " to " + _service.get("Status") + " silently");
	}
		
	this.setState = function setState(State) {
		_service.set("Status", State);
		_service.set("Active", true);
		_service.notify("Status");
		_DateUpdated = Date.now();
		debug("Setting Counter " + _service.get("Counter") + " to " + _service.get("Status"));
		debug("Maker = " + _service.get("Maker"));
		if (_service.get("Maker")===1) {
			debug("")
			sendIFTTT("Counter",_service,get("Counter"),new Date(_DateUpdated).toISOString());
		}
	}
	
	this.getDateUpdated = function getDateUpdated () {
		return _DateUpdated;
	}
	
	this.setMaker = function setMaker(maker) {
		if (maker===true) {
			_service.set("Maker",1);
		} else {
			_service.set("Maker",0);
		}
	}
	
	this.getMaker = function getMaker() {
		return _service.get("Maker");
	}

	//this.requestState = function requestState(state) {
	//	debug("Requesting "+ state + " on zone " + getZone());
		//				var commandString = "O!" + this.get("Zone").toInt().toString(16) + "0" + inputs.NewStatusValue;
		//				debug("Sending " + commandString + "to alarm");
		//				alarm.sendCommand(commandString);

	//}
}

function AlarmControl() {

	var thisAlarm = this;
	var _DateUpdated = Date.now();
	var _device = new UPNPService("Alarm",0, 0, true, "Comfort Alarm");	
	var _service = _device.createService({
		domain: "www.cytech.com",
		type: "alarm",
		serviceId: "alarm", 
		version: "1",
		implementation: {
			GetSecurityMode: function(inputs){
				return {RetCounterValue: this.get("SecurityMode")}
			},
			GetMaker: function(inputs){
				return {RetMakerValue: this.get("Maker")}
			},
			GetStatus: function(inputs){
				return {RetStatusValue: this.get("Status")}
			},
			GetActive: function(inputs){
				return {RetActiveValue: this.get("Active")}
			},
			SetMaker: function(inputs){
				debug("Maker Value Set");
				this.set("Maker", inputs.NewMakerValue);
				this.notify("Maker");
				
			},
			SetSecurityMode: function(inputs){
				debug("Security Mode Request");
				thisAlarm.requestSecurityMode(inputs.NewSecurityModeValue);
			},
			SetStatus: function(inputs){
				debug("Status Value Set");
				thisAlarm.requestState(inputs.NewStatusValue);
			},
			SetRunResponse: function(inputs){
				debug("Response Execution Invoked");
				thisAlarm.invokeResponse(inputs.NewRunResponseValue);
			}
		},
		description: {
			actions: {
				GetSecurityMode: {
					outputs: {
						RetSecurityModeValue: "SecurityMode"
					}
				},
				GetMaker: {
					outputs: {
						RetMakerValue: "Maker"
					}
				},
				GetStatus: {
					outputs: {
						RetStatusValue: "Status"
					}
				},
				GetActive: {
					outputs: {
						RetActiveValue: "Active"
					}
				},
				SetSecurityMode: {
					inputs: {
						NewSecurityModeValue: "SecurityMode"
					}
				},				
				SetMaker: {
					inputs: {
						NewMakerValue: "Maker"
					}
				},
				SetStatus: {
					inputs: {
						NewStatusValue: "Status"
					}
				},
				SetRunResponse: {
					inputs: {
						NewRunResponseValue: "RunResponse"
					}
				}	
			},	
			variables: {SecurityMode: "string", Status: "int", Maker: "boolean", Active: "boolean", RunResponse: "string"}	
		}	 
	});
	debug("Setting up alarm control");
	_service.set("Status",0);
	_service.set("SecurityMode","O");
	_service.set("Active",false);
	_service.set("Maker",0);

	this.getSecurityMode = function getSecurityMode() {
		return _service.get("SecurityMode");
	}

	this.setSecurityMode = function setSecurityMode(mode) {
		// O,A,N,D,V relate to Off, Away, Night, Day and Vacation Modes
		// W = Arming, No Zones Open (Alarm Ready)
		// X = Arming, Some Zones Open (Alarm Not Ready)
		// Y,Z = Entry / Exit Delay Started
		var _securityMode = ["O","A","N","D","V","W","X","Y","Z"]
		if (_securityMode[mode]) {
			_service.set("SecurityMode",_securityMode[mode]);
			_service.notify("SecurityMode");
		}
	}

	this.invokeResponse = function invokeResponse(response) {
		if ((response>=0) && (response<=255)) {
			var _commandString = "R!" + toHexByte(parseInt(response,10));
			alarm.sendCommand(_commandString);	
		}	
	}

	this.requestSecurityMode = function requestSecurityMode(request) {
		var _modeLocalRequest = null;
		var _modeTypeRequest = null;
		if (request.substring(0,1)=="L") {
			_modeLocalRequest = "m!";
		} else if (request.substring(0,1)=="R") {
			_modeLocalRequest = "M!";
		}
		if (request.substring(1,2)=="O") {
			_modeTypeRequest = "00";
		} else if (request.substring(1,2)=="A") {
			_modeTypeRequest = "01";
		} else if (request.substring(1,2)=="N") {
			_modeTypeRequest = "02";
		} else if (request.substring(1,2)=="D") {
			_modeTypeRequest = "03";
		} else if (request.substring(1,2)=="V") {
			_modeTypeRequest = "04";
		}
		if ((_modeLocalRequest) && (_modeTypeRequest)) {
			var _commandString = _modeLocalRequest + _modeTypeRequest + request.substring(2);
			alarm.sendCommand(_commandString);
		}
		// get an update on the alarm state
		alarm.sendCommand("a?");
	}

	this.getState = function getState() {
		return _service.get("Status");
	}

	this.requestState = function requestState(state){
		var _counter = toHexByte(counter)
		var _state = toHexByte(parseInt(state))
		debug ("->this.requestState:" + state + "for counter:" + _counter);
		var commandString = "C!" + _counter + _state;
		debug("Sending " + commandString + "to alarm");
		alarm.sendCommand(commandString);
	}

	this.setStateSilent = function setStateSilent(State) {
		_service.set("Status", State);
		_service.set("Active", true);
		_service.notify("Status");
		_DateUpdated = Date.now();
		debug("Setting Counter " + _service.get("Counter") + " to " + _service.get("Status") + " silently");
	}
		
	this.setState = function setState(State) {
		_service.set("Status", State);
		_service.set("Active", true);
		_service.notify("Status");
		_DateUpdated = Date.now();
		debug("Setting Counter " + _service.get("Counter") + " to " + _service.get("Status"));
		debug("Maker = " + _service.get("Maker"));
		if (_service.get("Maker")===1) {
			debug("")
			sendIFTTT("Counter",_service,get("Counter"),new Date(_DateUpdated).toISOString());
		}
	}
	
	this.getDateUpdated = function getDateUpdated () {
		return _DateUpdated;
	}
	
	this.setMaker = function setMaker(maker) {
		if (maker===true) {
			_service.set("Maker",1);
		} else {
			_service.set("Maker",0);
		}
	}
	
	this.getMaker = function getMaker() {
		return _service.get("Maker");
	}

	//this.requestState = function requestState(state) {
	//	debug("Requesting "+ state + " on zone " + getZone());
		//				var commandString = "O!" + this.get("Zone").toInt().toString(16) + "0" + inputs.NewStatusValue;
		//				debug("Sending " + commandString + "to alarm");
		//				alarm.sendCommand(commandString);

	//}
}
function Flag(flag, flagdata) {
	var active=true;
	var thisFlag = this;
	var _DateUpdated = Date.now();
	var _device = new UPNPService("Flag",flag, flag+700, active, flagdata.Name);	
	var _service = _device.createService({
		domain: "www.cytech.com",
		type: "flag",
		serviceId: "flag", 
		version: "1",
		implementation: {
			GetCounter: function(inputs){
				return {RetFlagValue: this.get("Flag")}
			},
			GetMaker: function(inputs){
				return {RetMakerValue: this.get("Maker")}
			},
			GetStatus: function(inputs){
				return {RetStatusValue: this.get("Status")}
			},
			GetActive: function(inputs){
				return {RetActiveValue: this.get("Active")}
			},
			SetMaker: function(inputs){
				debug("Maker Value Set");
				this.set("Maker", inputs.NewMakerValue);
				this.notify("Maker");
				
			},
			SetStatus: function(inputs){
				debug("Status Value Set");
				thisFlag.requestState(inputs.NewStatusValue);
			}

		},
		description: {
			actions: {
				GetFlag: {
					outputs: {
						RetCounterValue: "Flag"
					}
				},
				GetMaker: {
					outputs: {
						RetMakerValue: "Maker"
					}
				},
				GetStatus: {
					outputs: {
						RetStatusValue: "Status"
					}
				},
				GetActive: {
					outputs: {
						RetActiveValue: "Active"
					}
				},				
				SetMaker: {
					inputs: {
						NewMakerValue: "Maker"
					}
				},
				SetStatus: {
					inputs: {
						NewStatusValue: "Status"
					}
				}	
			},	
			variables: {Flag: "int", Status: "boolean", Maker: "boolean", Active: "boolean", }	
		}	 
	});
	debug("Setting up flag:" + flag);
	_service.set("Status",0);
	_service.set("Flag",flag);
	_service.set("Active",false);
	_service.set("Maker",0);

	this.getFlag = function getFlag() {
		return _service.get("Flag");
	}
	
	this.setFlag = function setFlag(flag) {
		_service.set("Flag",flag);
	}
	
	this.getState = function getState() {
		return _service.get("Status");
	}

	this.requestState = function requestState(state){
		var _flag = toHexByte(flag);
		var _state = "00"
		if (state=="1") {
			_state = "01"
		}
		debug ("->this.requestState:" + state + "for flag:" + _flag);
		var commandString = "F!" + _flag + _state;
		debug("Sending " + commandString + "to alarm");
		alarm.sendCommand(commandString);
	}

	this.setStateSilent = function setStateSilent(State) {
		_service.set("Status", State);
		_service.set("Active", true);
		_service.notify("Status");
		_DateUpdated = Date.now();
		debug("Setting Flag " + _service.get("Flag") + " to " + _service.get("Status") + " silently");
	}
		
	this.setState = function setState(State) {
		_service.set("Status", State);
		_service.set("Active", true);
		_service.notify("Status");
		_DateUpdated = Date.now();
		debug("Setting Flag " + _service.get("Flag") + " to " + _service.get("Status"));
		debug("Maker = " + _service.get("Maker"));
		if (_service.get("Maker")===1) {
			debug("")
			sendIFTTT("Flag",_service,get("Counter"),new Date(_DateUpdated).toISOString());
		}
	}
	
	this.getDateUpdated = function getDateUpdated () {
		return _DateUpdated;
	}
	
	this.setMaker = function setMaker(maker) {
		if (maker===true) {
			_service.set("Maker",1);
		} else {
			_service.set("Maker",0);
		}
	}
	
	this.getMaker = function getMaker() {
		return _service.get("Maker");
	}

}
// Comfort Main
function Comfort() {
	var thisAlarm = this;
	var connected = false;
	var poll = false;
	var poll_state = 0;
	var _zones = [];;
	var _outputs = [];
	var _counters = [];
	var _flags = [];
	var _responses = [];
	var _alarmControl = null;
	var user = "";
	const stx = String.fromCharCode(3);
	const etx = String.fromCharCode(13);
	var client;
	var _port;
	var _address;
	var _pin;
	var _pingCounter = 0;
	var _inputBuffer = "";

	var _maxzones = 96;
	var _maxoutputs = 248;
	var _maxcounters = 254;
	var _maxflags = 254;
	var _maxresponses = 254;

	var _zonearray = (config["zones"]) ? config["zones"].split(','):[]
	var _outputarray = (config["outputs"]) ? config["outputs"].split(','):[]
	var _counterarray = (config["counters"]) ? config["counters"].split(','):[]
	var _flagarray = (config["flags"]) ? config["flags"].split(','):[]
	var _responsearray = (config["responses"]) ? config["responses"].split(','):[]


	var _keepalive = (config["keepalive"]) ? parseInt(config["keepalive"],10):5000

	debug ("devices z:"+_zonearray.length +" o:"+ _outputarray.length + " c:" + _counterarray.length + " f:" + _flagarray.length + " r:" + _responsearray.length)

	// Init Counters
	for (var counter = 0;counter <= _maxcounters;counter++) {
		if (_counterarray.indexOf(counter.toString())>-1) {
			_counters[counter] = new Counter(counter, alarmConfig.getCounter(counter));
		} else {
			_counters[counter] = null;
		}
	}

	// Init Flags
	for (var flag = 1;flag <= _maxflags;flag++) {
		if (_flagarray.indexOf(flag.toString())>-1) {
			_flags[flag] = new Flag(flag, alarmConfig.getFlag(flag));
		} else {
			_flags[flag] = null;
		}
	}

	// Init Zones and Outputs
	for (var zone = 1; zone <= _maxzones; zone++) {
		if (_zonearray.indexOf(zone.toString())>-1) {
			_zones[zone] = new Zone(zone, alarmConfig.getZone(zone));
		} else {
			_zones[zone] = null;
		}
	}	

	for (var zone = 1; zone <= _maxoutputs; zone++) {
		if (_outputarray.indexOf(zone.toString())>-1) {
			_outputs[zone] = new Output(zone, alarmConfig.getOutput(zone));
		} else {
			_outputs[zone] = null;
		}
	}

	// Init Alarm Control
	_alarmControl = new AlarmControl();


	this.getZone = function getZone(zone) {
		return _zones[zone];
	}

	this.getOutput = function getOutput(zone) {
		return _outputs[zone]
	}

	var keepAlive = setInterval(function () {
		if (connected===true) {
			client.write(stx + "cc" + pad(_pingCounter,2) + etx);
			_pingCounter++;
			if (_pingCounter>99) {
				_pingCounter = 0;
				}
			}
		},_keepalive);
	

	this.connect = function connect (port, address, pin) {
		if (connected===false) {
			debug('->connect');
			_port = port;
			_address = address;
			_pin = pin;
			create();
			debug(_port +' '+ _address);
			client.connect(_port,_address);
		}
	}

	function doReset(error) {
		debug('->doReset');
		if (error===true) {
			debug(error);
		}
		client.end();
		connected = false;
		create();
		setTimeout(function (){client.connect(_port,_address)},1000);

	}

	this.sendCommand = function sendCommand(commandString) {
		debug('->sending command:' + commandString)
		if (connected ===true) {
			client.write(stx + commandString + etx);
		}
	}

	function doLogin() {
		debug('->doLogin');
		if (connected === false) {
			client.write(stx + 'LI' + _pin + etx);
		}
	}
	function create() {
		debug('->create')
		client = new net.Socket();
		client.on('error', function(message){debug(message)});
		client.on('close', function(had_error) {debug('->client.close');connected = false; doReset(had_error)});
		client.on('connect',function() {doLogin()});
		client.on('data',function(data) {processString(data)});
		client.on('end',function() {debug('->client.end -> something happened here')});
		client.setTimeout(1000000, function() {debug('->client.Timeout -> something happened here');doReset(false)});
	}

	// Process the buffer from the alarm
	function processString(alarmBuffer) {
		debug('->processString');
		for (var value of alarmBuffer.values()) {
			if (value===3) {
				// start of packet
				_inputBuffer = "";
			} else if (value===13) {
				parseResponse(_inputBuffer);
			} else {
				_inputBuffer = _inputBuffer + String.fromCharCode(value);
			}	
		}		
	}
	
	function parseResponse(response) {
		if (response===null || response.length<2) {
			return;
		}
		debug(response);
		var operator = response.substring(0,2);
		var value = response.substring(2);
		switch (operator) {
			case 'a?':
				// Alarm Information Reply
				var alarmType = parseInt(value.substring(0,2),16);
				_alarmControl.setState(alarmType);
				break;
			case 'A?':
				// Analogue Value Reported
				var zone = parseInt(value.substring(0,2),16);
				var zonevalue = parseInt(value.substring(2),16);
				_zones[zone].setAnalogue(zonevalue);
			case 'AL':
				// Alarm Type Report
				var alarmType = parseInt(value.substring(0,2),16);
				_alarmControl.setState(alarmType);
				break;
			case 'AM':
				// System (Non Detector) Alarm Report
				break;
			case 'AR':
				// System (Non Detector) Alarm Restore
				break;
			case 'b?':
				// Reply to Bypass all zone query
				break;
			case 'B?':
				// Reply to Bypass zone query
				break;
			case 'BP':
				//Beep on speaker report
				break;
			case 'BY':
				// Bypass Zone Report
				var zone = parseInt(value.substring(0,2),16);
				var zonevalue = parseInt(value.substring(2),16);
				if (_zones[zone]) {
					if (zonevalue == 0)  {
						_zones[zone].setBypass(0);
					} else {
						_zones[zone].setBypass(1);
					}
				}
				break;
			case 'cc':
				// echo reply to cc command
				debug(response);
				break;
			case 'C?':
				// Counter value Reply to C? Request
				var counter = parseInt(value.substring(0,2),16);
				var countervalue = parseInt(value.substring(2,4),16);
				if (_counters[counter]) {
					_counters[counter].setState(countervalue);
				}
				break;
			case 'CI':
				// Learned IR code data reply
				break;
			case 'CT':
				// counter changed report
				var counter = parseInt(value.substring(0,2),16);
				var countervalue = parseInt(value.substring(2,4),16);
				debug("Counter Value = " + value.substring(2,4) + ":" + countervalue);
				if (_counters[counter]) {
					_counters[counter].setState(countervalue);
				}
				break;
			case 'cm':
				// Control Menu reply and report
				break;
			case 'DB':
				// Doorbell Pressed Report
				break;
			case 'D*':
				// Status reply from DSP to DC command
				break;
			case 'DT':
				// Date and Time report
				break;
			case 'DI':
				// Dial Up Report
				break;
			case 'EV':
				// Event Log Report
				break;
			case 'ER':
				// Alarm Ready / Not Ready Report
				var ready = parseInt(value.substring(0,2),16);
				if (ready==0) {
					_alarmControl.setSecurityMode(5);	
				} else {
					_alarmControl.setSecurityMode(6);
				}
				break;
			case 'EX':
				// Entry / Exit Delay Started Report
				var ready = parseInt(value.substring(0,2),16);
				if (ready==0) {
					_alarmControl.setSecurityMode(7);	
				} else {
					_alarmControl.setSecurityMode(8);
				}
				break;
			case 'f?':
				// reply to query all flags
				break;
			case 'F?':
				// reply to Flag Request
				var flag = parseInt(value.substring(0,2),16);
				var flagvalue = parseInt(value.substring(2),16);
				if (_flags[flag]) {
					_flags[flag].setState(flagvalue);
				}
				break;
			case 'FL':
				// flag status report
				var flag = parseInt(value.substring(0,2),16);
				var flagvalue = parseInt(value.substring(2),16);
				if (_flags[flag]) {
					_flags[flag].setState(flagvalue);
				}
				break;
			case 'id':
				// reply to ID command
				break;
			case 'I?':
				//input activation report
				var zone = parseInt(value.substring(0,2),16);
				var zonevalue = parseInt(value.substring(2),16);
				if (_zones[zone]) {
					_zones[zone].setState(zonevalue);
				}
				break;
			case 'IP':
				//input activation report
				var zone = parseInt(value.substring(0,2),16);
				var zonevalue = parseInt(value.substring(2),16);
				if (_zones[zone]) {
					_zones[zone].setState(zonevalue);
				}
				break;
			case 'IR':
				//IR activation report
				break;
			case 'IX':
				// IR Code received report
				break;
			case 'KL':
				// Keypad LEDS status Report
				break;
			case 'Kr':
				// read from KT03 memory reply
				break;
			case 'Kw':
				// write to KT03 acknowledge reply
				break;
			case 'LR':
				// Login Report
				break;
			case 'LU':
				// User Logged in Report
				if ((value==='00')===false) {
					connected=true;
					poll = true;
					poll_state = 0;
					user = value;
				} else {
					connected = false;
					user = "";
				}
				break;
			case 'NA':
				// Error Response
				break;
			case 'OK':
				// Command Acknowledged Reply
				break;
			case 'OP':
				// Output Activation Report
				var zone = parseInt(value.substring(0,2),16);
				var zonevalue = parseInt(value.substring(2),16);
				if (_outputs[zone]) {
					_outputs[zone].setState(zonevalue);
				}
				break;
			case 'OQ':
				// Virtual Output status request
				break;
			case 'M?':
				// Mode change Report
				var securityMode = parseInt(value.substring(0,2),16);
				_alarmControl.setSecurityMode(securityMode);
				break;
			case 'MD':
				// Mode Change Report
				var securityMode = parseInt(value.substring(0,2),16);
				_alarmControl.setSecurityMode(securityMode);
				break;
			case 'PT':
				// Pulse Activation Report
				break;
			case 'r?':
				// Sequential Register Query
				break;
			case 'RA':
				// Return value from DA (Do Action)
				break;
			case 'RP':
				// Phone Ring Report
				break;
			case 'SS':
				// Status report from external UCM
				break;
			case 'SN':
				// Serial Number Reply
				break;
			case 's?':

				break;	
			case 'sr':
				// Sensor Register report
				break;
			case 'TT':
				// Monitor external bus communication for special UCMs
				break;
			case 'XF':
				// X10 House/Function Code Report
				break;
			case 'XR':
				// X10 Received Report
				break;
			case 'XT':
				// X10 Transmitted Report
				break;
			case 'XU':
				// X10 house/unit code received Report
				break;
			case 'WE':
				// acknowledge reply from WD command
				break;
			case 'Y?':
				// Reply to Y? request all output status
				break;
			case 'y?':
				// Reply to Y? request all output status	
			case 'Z?':
				// reply to report all zones
				break;
			case 'z?':
				// reply to report all zones
				break;
			case '??':
				// checksum error or error in message format
				break;
			default:
				// Something Else Happened
		}
		if (poll===true) {
			switch(poll_state) {
				case 0:
					client.write(stx + 'a?' + etx);
					break;
				case 1:
					client.write(stx + 'b?' + etx);
					break;
				case 2:
					//client.write(stx + 'f?'  + etx);
					for (var flag = 1; flag <= _maxflags;flag++) {
						if (_flags[flag]) {
							client.write(stx + "F?" + toHexByte(flag) + etx);
						}
					}
					break;
				case 3:
					client.write(stx + 'r?000016' + etx);
					break;
				case 4:
					client.write(stx + 's?00'  + etx);
					break;
				case 5:
					client.write(stx + 'Y?'  + etx);
					break;
				case 6:
					client.write(stx + 'y?'  + etx);
					break;
				case 7:
					client.write(stx + 'Z?'  + etx);
					break;
				case 8:
					client.write(stx + 'z?' + etx);
					break;
				case 9:
					for (var counter = 0;counter <= _maxcounters;counter++) {
						if (_counters[counter]) {
							client.write(stx + "C?" + toHexByte(counter) + etx);
						}
					}
				case 10:
					client.write(stx + 'M?'  + etx);
					break;	
				default:
					poll_state = 0;
					poll = false;
				}
			poll_state ++;				
		}
	}
}	




// Helper Functions
function pad(num, size) {
    var s = num+"";
    while (s.length < size) s = "0" + s;
    return s;
}

function toHex(value, length) {
	var _value = value.toString(16)
	while (_value.length<length) {
		_value = "0" + _value
	}
	return _value
}

function toHexByte(value) {
	return toHex(value,2);
}


// Web Interface 
function configureHTTP(){
	AlarmHTTP.use('/static', express.static('static'));
	AlarmHTTP.get('/', function (request, response) {
		var output = "";
		for (var zone = 0; zone<63; zone++) {
			output = output + "\nZone: " + zone + ", State:"+ alarm.getZone(zone).getState() + ", Last Changed:" + new Date(alarm.getZone(zone).getDateUpdated()).toISOString();
		}	
		response.end('It works!! Path Hit: ' + request.url + output);
	});
	AlarmHTTP.listen(8080,function () {debug('Web server listening on 8080')});
};

function configureUPNP(){
	server.listen('8082');
	peer = upnp.createPeer({prefix: "/upnp",server: server});
	peer.on("ready", function(peer){debug("UPNP Ready")});
	peer.on("close", function(peer){debug("UPNP Closed")});
	peer.start();
	device = peer.createDevice({
		autoAdvertise: true,
		productName: "ComfortAlarm",
		productVersion: "0.0.1",
		domain: "www.cytech.com",
		type: "ComfortAlarm",
		version: "1",
		friendlyName: "ComfortAlarm",
		manufacturer: "Matt Brain",
		manufacturerURL: "http://www.cytech.com",
		modelName: "ComfortAlarm",
		modelDescription: "Cytech Comfort Alarm UPNP Interface",
		modelNumber: "0.0.1",
		modelURL: "http://www.cytech.com",
		serialNumber: "1234-1234-1234-1234",
		UPC: "123456789012"
	})
};

// IFTTT Function
function sendIFTTT(trigger, value1, value2, value3) {
	var body = JSON.stringify({
		value1: value1,
		value2: value2,
		value3: value3
	})
	var request = new http.ClientRequest({
		hostname: "maker.ifttt.com",
		port: 80,
		path: "/trigger/" + trigger + "/with/key/" + makerkey,
		method: "POST",
		headers: {
			"Content-Type": "application/json",
			"Content-Length" : Buffer.byteLength(body)
		}
	})
	request.end(body);
};

function UPNPService  (type, num, portnum, autoAdvertise, friendlyName) {
	debug("Creating UPNP Service " + type + ":" + num + " on port:" + portnum + " AutoAdvertise:"+ autoAdvertise);
	var _server = http.createServer();
	_server.on("connection", function (socket) {
    	socket.setNoDelay(true);
	});
	_server.listen(portnum + portstart);
	var _peer = upnp.createPeer({prefix: "/upnp",server: _server});
	_peer.on("ready", function(_peer){debug("UPNP Ready")});
	_peer.on("close", function(_peer){debug("UPNP Closed")});
	_peer.start()

	var _device = _peer.createDevice({
		autoAdvertise: autoAdvertise,
		productName: "ComfortAlarm"+type,
		productVersion: "0.0.1",
		domain: "www.cytech.com",
		type: "ComfortAlarm" + type,
		version: "1",
		friendlyName: friendlyName,
		manufacturer: "Matt Brain",
		manufacturerURL: "http://www.cytech.com",
		modelName: "ComfortAlarm" + type,
		modelDescription: "Cytech Comfort Alarm " + type + " UPNP Interface",
		modelNumber: "0.0.1",
		modelURL: "http://www.cytech.com",
		serialNumber: "1234-1234-1234-1234",
		UPC: "123456789012",
		uuid: "ComfortAlarm" + type + num
	})
	return _device;
}

function ComfortConfiguration(filename) {
    var comfortjs = null
    xmlreader = new xml2js.Parser()
    // read configuration file

    debug ("Reading configuration from " + filename)
    
    var input = fs.readFileSync(filename);
    xmlreader.parseString(input, function(err, result) {
        comfortjs = result;
    })

    this.getMaxZones = function getMaxZones() {
        var slaves = parseInt(comfortjs.Configuration.Modules[0].$.Slaves);
        var lem = comfortjs.Configuration.Modules[0].$.LEM03 == "true";

        if (lem) {
            return 24;
        } else {
            return 16 + (16 * slaves);
        }
    }

    this.getMaxOutputs = function getMaxOutputs() {
        var slaves = parseInt(comfortjs.Configuration.Modules[0].$.Slaves);
        var lem = comfortjs.Configuration.Modules[0].$.LEM03 == "true";

        if (lem) {
            return 8;
        } else {
            return 16 + (16 * slaves);
        }
    }

    this.getSCSRIO = function getSCSRIO () {
        return parseInt(comfortjs.Configuration.Modules[0].$.ScsRio);
    }


    this.getZone = function getZone(zone) {
		var result = null
		comfortjs.Configuration.Zones[0].Zone.forEach(function (item) {
			if (parseInt(item.$.Number) == zone) {
				debug ("Requested Zone " + zone + ", sent " + item.$.Number)
				result = item.$;
		}})
		return result;        
    }

	this.getOutput = function getOutput(output) {
		var result = null
		comfortjs.Configuration.Outputs[0].Output.forEach(function (item) {
			if (parseInt(item.$.Number) == output) {
				debug ("Requested Output " + output + ", sent " + item.$.Number)
				result =  item.$;
		}})	
		return result;
    }

	this.getCounter = function getCounter(counter) {
		var result = null
		comfortjs.Configuration.Counters[0].Counter.forEach(function (item) {
			if (parseInt(item.$.Number) == counter) {
				debug ("Requested Counter " + counter + ", sent " + item.$.Number)
				result =  item.$;
		}})	
		return result;
	}

	this.getFlag = function getFlag(flag){
		var result = null
		comfortjs.Configuration.Flags[0].Flag.forEach(function (item) {
			if (parseInt(item.$.Number) == flag) {
				debug ("Requested Flag " + flag + ", sent " + item.$.Number)
				result =  item.$;
		}})	
		return result;
	}


}

function debug (message, severity) {
	if (typeof severity === 'undefined') {
		severity = 'debug'
	}
	if (enableDebug) {
		console.log(new Date().toISOString() + " " + severity + ":" + message)

	}
}
