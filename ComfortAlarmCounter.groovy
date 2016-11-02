/**
 *
 * UPNP Comfort Alarm Counter Interface
 * Copyright Matt Brain (matt.brain@gmail.com)
 * https://github.com/mattbrain/Cytech-Comfort
 *
 *
 *
 */
metadata {
	definition (name: "ComfortAlarm Counter", namespace: "mattbrain", author: "Matt Brain") {
    	capability "Actuator"
    	capability "Switch"
        capability "Refresh"
        capability "Sensor"
        capability "switchLevel"
        
        attribute "currentIP", "string"
        attribute "maker", "string"
        
        command "subscribe"
        command "unsubscribe"
        command "processEvent"
        command "makerToggle"
        command "on"
        command "off"
        command "doOn"
        command "doOff"
        command "levelUp"
        command "levelDown"
	}

	simulator {
		// TODO: define status and reply messages here
	}

	preferences {
    	input name: "onAction", type: "enum", title: "On Action", options: ["Set to Min", "Set to Max", "Increment", "Decrement"], description: "Enter On Action", required: true
		input name: "offAction", type: "enum", title: "Off Action", options: ["Set to Min", "Set to Max", "Increment", "Decrement"], description: "Enter Off Action", required: true
		input name: "minValue", type: "decimal", title: "Minimum Counter Value", description: "Enter the minimum SmartThings can set the value to", required: true
        input name: "maxValue", type: "decimal", title: "Maximum Counter Value", description: "Enter the maximum SmartThings can set the value to", required: true
    }
    
    
	tiles (scale: 2) {
    
    /*
  		 multiAttributeTile(name:"rich-control", type: "switch", canChangeIcon: true){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                 attributeState "on", label:'${name}', action:"off", icon:"st.illuminance.illuminance.light", backgroundColor:"#79b821", nextState:"turningOff"
                 attributeState "off", label:'${name}', action:"on", icon:"st.illuminance.illuminance.dark", backgroundColor:"#ffffff", nextState:"turningOn"
                 attributeState "turningOn", label:'${name}', action:"off", icon:"st.illuminance.illuminance.light", backgroundColor:"#79b821", nextState:"turningOff"
                 attributeState "turningOff", label:'${name}', action:"on", icon:"st.illuminance.illuminance.dark", backgroundColor:"#ffffff", nextState:"turningOn"
  				 attributeState "flashing", label:'${name}', action:"off", icon:"st.illuminance.illuminance.bright", backgroundColor:"#79b821", nextState:"turningOff"
  				 attributeState "offline", label:'${name}', icon:"st.illuminance.illuminance.dark", backgroundColor:"#ff0000"
                 attributeState "unknown", label:'${name}', icon:"st.illuminance.illuminance.dark", backgroundColor:"#ffffff"
 			}
        }
	
        standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
            state "on", label:'${name}', action:"off", icon:"st.illuminance.illuminance.light", backgroundColor:"#79b821", nextState:"turningOff"
            state "off", label:'${name}', action:"on", icon:"st.illuminance.illuminance.dark", backgroundColor:"#ffffff", nextState:"turningOn"
            state "turningOn", label:'${name}', action:"off", icon:"st.illuminance.illuminance.light", backgroundColor:"#79b821", nextState:"turningOff"
            state "turningOff", label:'${name}', action:"on", icon:"st.illuminance.illuminance.dark", backgroundColor:"#ffffff", nextState:"turningOn"
   			state "flashing", label:'${name}', action:"off", icon:"st.illuminance.illuminance.bright", backgroundColor:"#79b821", nextState:"turningOff"
   			state "offline", label:'${name}', icon:"st.illuminance.illuminance.dark", backgroundColor:"#ff0000"
            state "unknown", label:'${name}', icon:"st.illuminance.illuminance.dark", backgroundColor:"#ffffff"
        }
		*/
        multiAttributeTile(name:"valueTile", type:"generic", width:6, height:4) {
    		tileAttribute("device.level", key: "PRIMARY_CONTROL") {
        		attributeState "default", label:'${currentValue}' 
            }
    		tileAttribute("device.level", key: "VALUE_CONTROL") {
        		attributeState "VALUE_UP", action: "levelUp"
        		attributeState "VALUE_DOWN", action: "levelDown"
    		}
		}
        
        valueTile("valueTileMain", "device.level", decoration: "flat", width: 2, height: 2) {
            state "level", label: '${currentValue}', icon: "st.Kids.kids6"
        }
        
        standardTile("refresh", "device.refresh", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"Refresh", action:"refresh", icon:"st.secondary.refresh"
        }
        standardTile("on", "device.switchon", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"Turn On", action:"doOn", icon:"st.illuminance.illuminance.light"
        }
        standardTile("off", "device.switchoff", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"Turn Off", action:"doOff", icon:"st.illuminance.illuminance.dark"
        }
		standardTile("up", "device.switchflash", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"Up", action:"levelUp", icon:"st.illuminance.illuminance.light"
        }
		standardTile("down", "device.switchflashonce", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"Down", action:"levelDown", icon:"st.illuminance.illuminance.light"
        }
        standardTile("maker", "device.maker", height: 2, width: 2, decoration: "flat") {
            state("on", label:'On', nextState: "pending", action:"makerToggle", icon:"st.unknown.zwave.static-controller",backgroundColor:"#00ff00")
            state("off", label:'Off', nextState: "pending", action:"makerToggle", icon:"st.unknown.zwave.static-controller",backgroundColor:"#ffffff")
            state("pending", label:'waiting',action:"makerToggle", icon:"st.unknown.zwave.static-controller",backgroundColor:"#ff0000")
        }
		main "valueTileMain"
		details (["valueTile", "on", "up", "refresh", "off" , "down",  "maker"])
	}
}

// parse events into attributes
def parse(String description) {
	log.debug "->parse ${description}"
    def msg = parseLanMessage(description)
	def headerString = msg.header
    def bodyString = msg.body
    log.debug "Parsed: ${headerString}, ${bodyString}"
    def notifyString = getChildFromHeader(headerString)
    def sid = getSIDFromHeader(headerString)
    if (sid) {
    	// got a SID in the data, therefore must be a ack for request / renew
        if (getDataValue("sid")) {
        	if (getDataValue("sid")==sid){
            	log.debug "New sid = old sid - therefore renew worked"
            } else {
            	log.debug "New sid != old sid- therefore new subscription"
                unsubscribe(getDataValue("sid"))
                updateDataValue("sid",sid)
            }
        } else { 
        	log.debug "New sid, no existing SID in data"
        	updateDataValue("sid",sid)
            sendEvent(name: "switch", value: "unknown", descriptionText: "Waiting on event")
        }
        updateDataValue("lastSIDTime",Long.toString(now()))
    } else if (getHTTPStatusFromHeader(headerString)!="200") {
    	if (getDataValue("unsubscribe")) {
        	updateDataValue("unsubscribe","")
        } else {
    		log.debug "HTTP Error in parse ${getHTTPStatusFromHeader(headerString)}"
    		updateDataValue("sid","")
        	sendEvent(name: "switch", value: "offline", descriptionText: "Subscription Error")
        }
    }
}

def processEvent(sid, item, value) {
	log.debug "->processEvent ${sid}, ${item}, ${value}"
	if (sid==getDataValue("sid")) {
    	log.debug "->processEvent: Correct SID"
		if (item == "Status") {
            log.debug "->processEvent: Setting switch to ${value}"
    		sendEvent(name: "level", value: value, descriptionText: "Switch is ${value}")
    	} else if (item=="Maker") {
        	if (value=="0") {
            	value="off"
            } else {
            	value="on"
            }
            log.debug "->processEvent: Setting Maker to ${value}"
            sendEvent(name: "maker", value: value, descriptionText: "Maker is ${value}")
       }
    } else {
    	log.debug "->process event with incorrect sid, unsubscribing"
        upnpunsubscribe(sid)
	}
} 

void subscribe() {
	def lastSIDTime = 0
	if (getDataValue("lastSIDTime"))
    	{lastSIDTime = Long.parseLong(getDataValue("lastSIDTime"))}
    log.debug "->subscribe(), last subscription acknowledge ${(now() - lastSIDTime)/1000} seconds ago"
    
	if ((now() - lastSIDTime) > 604800000) {
    	// previous SID has expired, request new one
        log.debug("Clearing out expired subscription")
		updateDataValue("sid","")
        sendEvent(name: "switch", value: "offline", descriptionText: "Subscription Expired")
   		// update schedule
        schedule("10 0/5 * * * ?",subscribe) 
    }
	def address = getCallBackAddress()
    def headers
    if (getDataValue("sid")) {
    	log.debug "Renew Subscription"
        headers = [
        	HOST: getDataValue("ip") + ":" + getDataValue("port"),
			SID: getDataValue("sid"),
            TIMEOUT: "Second-604800"
           ]
    		
    } else {
    	
    	log.debug "New Subscription"
    	headers = [
        	HOST: getDataValue("ip") + ":" + getDataValue("port"),
            CALLBACK: "<http://${address}/notify/${this.device.deviceNetworkId}>",
            NT: "upnp:event",
            TIMEOUT: "Second-604800"
           ]
           sendEvent(name: "switch", value: "offline", descriptionText: "Subscription Requested")
    }
    def action = new physicalgraph.device.HubAction(
    	method: "SUBSCRIBE",
        path: getDataValue("subURL"),
        headers: headers
    )
    def result = sendHubCommand(action)
    log.debug "SUBSCRIBE ${suburl}: ${action}: ${result}" 
}

def upnpunsubscribe(sid) {
	log.debug "->Unsubscribing ${sid}"
    def headers = [
    HOST: getDataValue("ip") + ":" + getDataValue("port"),
	SID: sid
    ]
    def action = new physicalgraph.device.HubAction(
    	method: "UNSUBSCRIBE",
        path: getDataValue("subURL"),
        headers: headers
    )
    def result = sendHubCommand(action)
    updateDataValue("unsubscribe", "true")
}

def setOffline() {
    sendEvent(name: "switch", value: "offline", descriptionText: "The device is offline")
}

private Integer convertHexToInt(hex) {
 	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
 	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

def sync(ip, port) {
	def existingIp = getDataValue("ip")
	def existingPort = getDataValue("port")
	if (ip && ip != existingIp) {
		updateDataValue("ip", ip)
	}
	if (port && port != existingPort) {
		updateDataValue("port", port)
	}
}

private getCallBackAddress() {
    return device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
}


private getChildFromHeader(header) {
	def notifyPath = null
    if (header?.contains("NOTIFY")) {
		notifyPath = (header =~ /NOTIFY.*/)[0]
        notifyPath = notifyPath.split()[1]
        notifyPath -= "/notify/".trim()
    }
    return notifyPath
}

private getSIDFromHeader(header) {
	def sid = null
	if (header?.contains("SID: uuid:")) {
		sid = ( header =~ /SID: uuid:.*/)[0]
		sid -= "SID: ".trim()
	}
    return sid
}

private getHTTPStatusFromHeader(header) {
	def httpStatus = null
    if (header?.contains("HTTP/1.1")) {
		httpStatus = (header =~ /HTTP.*/)[0]
        httpStatus = httpStatus.split()[1]
    }
    return httpStatus
}

void doButton(action) {

    if (action) {
    	if (action == "Set to Min") {
            if (minValue) {
                doAction("SetStatus",[NewStatusValue:minValue])           
            } else {
                doAction("SetStatus",[NewStatusValue:0])
            }
        } else if (action == "Set to Max") {
        	if (maxValue) {
                doAction("SetStatus",[NewStatusValue:maxValue])           
            } else {
                doAction("SetStatus",[NewStatusValue:255])
            }
        } else if (action == "Increment") {
            levelUp();

        } else if (action == "Decrement") {
            levelDown()
        } else {
            log.debug "Unable to parse action ${onAction}"
        }
    } else {
    	log.debug "Action not set"
	}
}

void on() {
    if (onAction) {
        doButton(onAction)
    } else {
        doButton("Set to Max")
    }
}

void doOn() {
	if (maxValue) {
		doAction("SetStatus",[NewStatusValue:maxValue])
    } else {
       	doAction("SetStatus",[NewStatusValue:255])
    }
}

void doOff() {
	if (minValue) {
		doAction("SetStatus",[NewStatusValue:minValue])
    } else {
       	doAction("SetStatus",[NewStatusValue:0])
    }
}

void off() {
    if (offAction) {
        doButton(offAction)
    } else {
        doButton("Set to Min")
    }
}

void levelUp() {
	def level = device.currentValue("level") + 1
    if (maxValue) {
        if (level > maxValue) {
            level = maxValue;
        } else {
            if (level>255) {
                level = 255;
            }
        }
    }
	doAction("SetStatus",[NewStatusValue:level])
}

void levelDown() {
	def level = device.currentValue("level") - 1
    if (minValue) {
        if (level < minValue) {
            level = minValue;
        } else {
            if (level<0) {
                level = 0;
            }
        }
    }
	doAction("SetStatus",[NewStatusValue:level])
}


void makerToggle() {
	log.debug "->makerToggle"
	if (device.currentValue("maker")=="off") {
        doAction("SetMaker", [NewMakerValue:1])
    } else {
        doAction("SetMaker", [NewMakerValue:0])
    }
    log.debug "Sending sendEvent"
}

void refresh() {
	log.debug "Manual Subscription renewal"
    renewSubscription() 
}

void setLevel(value) {
	log.debug "Setting Counter to " + value
	doAction("SetCounter", [NewCounterValue:value])
}

void renewSubscription() {
	log.debug "Subscription Renewal"
    updateDataValue("sid","")
    subscribe()
    schedule("10 0/5 * * * ?",subscribe) 
}


void updateMakerTile() {
	//sendEvent(name: "maker", value: "pending", descriptionText: "Waiting for a response from the device")
}

def doAction(action, Map body = [InstanceID:0, Speed:1]) {
	log.debug "doAction ${action}, ${body}"
    def result = new physicalgraph.device.HubSoapAction(
        path:    getDataValue("controlURL"),
        urn:     "urn:www.cytech.com:service:counter:1",
        action:  action,
        body:    body,
        headers: [Host:getDataValue("ip") + ":" + getDataValue("port"), CONNECTION: "close"]
    )
    //return result
    sendHubCommand(result)
}