/**
 *
 * UPNP Comfort Alarm Zone Interface
 * Copyright Matt Brain (matt.brain@gmail.com)
 * https://github.com/mattbrain/Cytech-Comfort
 *
 *
 *
 */
metadata {
	definition (name: "ComfortAlarm Zone", namespace: "mattbrain", author: "Matt Brain") {
    	capability "Motion Sensor"
        capability "Refresh"
        capability "Sensor"
        
        attribute "currentIP", "string"
        attribute "maker", "string"
        
        command "subscribe"
        command "unsubscribe"
        command "processEvent"
        command "makerToggle"
        command "bypassOff"
        command "bypassOn"
	}

	simulator {
		// TODO: define status and reply messages here
	}

	tiles (scale: 2) {
 		multiAttributeTile(name:"motion", type: "motion", canChangeIcon: true){
            tileAttribute ("device.motion", key: "PRIMARY_CONTROL") {
                 attributeState "active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0"
                 attributeState "inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
                 attributeState "offline", label:'offline', icon:"st.motion.motion.active", backgroundColor:"#ff0000"
                 attributeState "unknown", label:'unknown', icon:"st.motion.motion.active", backgroundColor:"#ffffff"
 			}
        }

	//	standardTile("motion", "device.motion", width: 2, height: 2) {
	//		state("active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0")
	//		state("inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff")
     // 		state("offline", label:'${name}', icon:"st.motion.motion.inactive", backgroundColor:"#ff0000")
	//	}

        standardTile("refresh", "device.switch", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        standardTile("maker", "device.maker", height: 2, width: 2, decoration: "flat") {
            state("On", label:'On', nextState: "pending", action:"makerToggle", icon:"st.unknown.zwave.static-controller",backgroundColor:"#00ff00")
            state("Off", label:'Off', nextState: "pending", action:"makerToggle", icon:"st.unknown.zwave.static-controller",backgroundColor:"#ffffff")
            state("pending", label:'waiting',action:"makerToggle", icon:"st.unknown.zwave.static-controller",backgroundColor:"#ff0000")
        }
        standardTile("bypass", "device.bypass", height: 2, width: 2, decoration: "flat") {
            state("On", label:'Zone Bypassed', nextState: "pending", action:"bypassOff", icon:"st.unknown.zwave.static-controller",backgroundColor:"#ff0000")
            state("Off", label:'Zone Active', nextState: "pending", action:"bypassOn", icon:"st.unknown.zwave.static-controller",backgroundColor:"#00ff00")
            state("pending", label:'waiting', icon:"st.unknown.zwave.static-controller",backgroundColor:"#ffffff")
        }
        
		main "motion"
		details (["motion", "refresh", "maker", "bypass"])
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
            sendEvent(name: "motion", value: "unknown", descriptionText: "Waiting on event")
        }
        updateDataValue("lastSIDTime",Long.toString(now()))
    } else if (getHTTPStatusFromHeader(headerString)!="200") {
    	if (getDataValue("unsubscribe")) {
        	updateDataValue("unsubscribe","")
        } else {
    		log.debug "HTTP Error in parse ${getHTTPStatusFromHeader(headerString)}"
    		updateDataValue("sid","")
        	sendEvent(name: "motion", value: "offline", descriptionText: "Subscription Error")
		}
	}
}

def processEvent(sid, item, value) {
	log.debug "->processEvent ${sid}, ${item}, ${value}"
	if (sid==getDataValue("sid")) {
    	log.debug "->processEvent: Correct SID"
		if (item == "Status") {
        	log.debug "->processEvent: State Update"
        	if (value=="0") {
            	value="inactive"
            } else {
            	value="active"
            }
            log.debug "->processEvent: Setting motion to ${value}"
    		sendEvent(name: "motion", value: value, descriptionText: "Motion is ${value}")
    	} else if (item=="Maker") {
        	if (value=="0") {
            	updateDataValue("maker","Off")
            	value="Off"
            } else {
            	value="On"
                updateDataValue("maker","On")
            }
            log.debug "->processEvent: Setting Maker to ${value}"
            sendEvent(name: "maker", value: value, descriptionText: "Maker is ${value}")
       } else if (item=="Bypass") {
        	if (value=="0") {
            	updateDataValue("bypass","Off")
            	value="Off"
            } else {
            	value="On"
                updateDataValue("bypass","On")
            }
            sendEvent(name: "bypass", value: value, descriptionText: "Bypass is ${value}")
       } else if (item=="ZoneTypeName") {
        	updateDataValue("zoneType",value);
       } else {
       		log.debug "Unimplemented Event received: " + item + " with value " + value + ", ignoring"
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
        sendEvent(name: "motion", value: "offline", descriptionText: "Subscription Expired")
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
           sendEvent(name: "motion", value: "offline", descriptionText: "Subscription Requested")
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
    sendEvent(name: "motion", value: "offline", descriptionText: "The device is offline")
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

void makerToggle() {
	log.debug "->makerToggle"
    //sendEvent(name: "maker", value: "on", descriptionText: "Waiting for a response from the device", displayed: true)
	if (getDataValue("maker")=="Off") {
        doAction("SetMaker", [NewMakerValue:1])
    } else {
    	updateDataValue("maker","Off")
        doAction("SetMaker", [NewMakerValue:0])
    }
    log.debug "Sending sendEvent"
	//sendEvent(name: "maker", value: "pending", descriptionText: "Waiting for a response from the device", displayed: true)
}

void bypassOn() {
	doAction("SetBypass", [NewBypassValue:1]);
}

void bypassOff() {
	doAction("SetBypass", [NewBypassValue:0]);
}

void updateMakerTile() {
	//sendEvent(name: "maker", value: "pending", descriptionText: "Waiting for a response from the device")
}

def doAction(action, Map body = [InstanceID:0, Speed:1]) {
	log.debug "doAction ${action}, ${body}"
    def result = new physicalgraph.device.HubSoapAction(
        path:    getDataValue("controlURL"),
        urn:     "urn:www.cytech.com:service:zone:1",
        action:  action,
        body:    body,
        headers: [Host:getDataValue("ip") + ":" + getDataValue("port"), CONNECTION: "close"]
    )
    //return result
    sendHubCommand(result)
}

void refresh() {
	log.debug "Manual Subscription renewal"
    renewSubscription() 
}


void renewSubscription() {
	log.debug "Subscription Renewal"
    updateDataValue("sid","")
    subscribe()
    schedule("15 0/5 * * * ?",subscribe) 
}