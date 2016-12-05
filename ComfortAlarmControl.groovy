/**
 *
 * UPNP Comfort Alarm Control Interface
 * Copyright Matt Brain (matt.brain@gmail.com)
 * https://github.com/mattbrain/Cytech-Comfort
 *
 *
 *
 */
metadata {
	definition (name: "ComfortAlarm Control", namespace: "mattbrain", author: "Matt Brain") {
    	capability "Actuator"
    	capability "Switch"
        capability "Refresh"
        capability "Sensor"
        capability "Alarm"
        capability "TamperAlert"
        
        attribute "currentIP", "string"
        attribute "maker", "string"
        
        command "subscribe"
        command "unsubscribe"
        command "processEvent"
        command "makerToggle"
        command "on"
        command "off"
        command "modeOff"
        command "modeAway"
        command "modeNight"
        command "modeDay"
        command "modeVacation"
        command "processResponse"

	}

	simulator {
		// TODO: define status and reply messages here
	}

	preferences {
    	input name: "alarmCode", type: "password", title: "Alarm Code", description: "Enter User Code", required: true
	}
    
	tiles (scale: 2) {
        standardTile("AlarmMode", "device.AlarmMode", width:3, height:3, canChangeIcon: false) {
            state "offline", label:'Offline', icon:"st.security.alarm.off", backgroundColor:"#ff0000"
            state "off", label:'Security Off', icon:"st.security.alarm.off", backgroundColor:"#00ff00"
            state "away", label:'Away Mode Active', icon:"st.security.alarm.on", backgroundColor:"#00ff00"
            state "night", label:'Night Mode Active', icon:"st.security.alarm.partial", backgroundColor:"#00ff00"
            state "day", label:"Day Mode Active", icon:"st.security.alarm.partial", backgroundColor:"#00ff00"
            state "vacation", label:"Vacation Mode Active", icon:"st.security.alarm.on", backgroundColor:"#00ff00"
            state "armOK", label:"Arming Alarm", icon:"st.security.alarm.off",backgroundColor:"#00ff00"
            state "armWait", label:"Arming Alarm, Zone Active", icon:"st.security.alarm.off",backgroundColor:"#ffa500"
            state "entryDelay", label:"Entry Delay", icon:"st.security.alarm.on", backgroundColor:"#ffa500"
            state "exitDelay", label:"Exit Delay", icon:"st.security.alarm.off", backgroundColor:"#ffa500"
        }

        standardTile("AlarmState", "device.AlarmState", width:3, height:3, canChangeIcon: false) {
            state "offline", label:'Offline', icon: "st.security.alarm.off", backgroundColor:"#ff0000"
            state "noAlarm", label:"No Alarm", icon:"st.security.alarm.clear", backgroundColor:"#00ff00"
            state "intruderAlarm", label:"Intruder", icon:"st.security.alarm.alarm", backgroundColor:"#ff0000"
            state "duress", label:"Duress",icon:"st.security.alarm.alarm", backgroundColor:"#ff0000"
            state "phoneLineTrouble", label:"Phone Line Trouble",icon:"st.secondary.tools", backgroundColor:"#ffa500"            
        }

        standardTile("refresh", "device.switch", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"Refresh", action:"refresh", icon:"st.secondary.refresh"
        }

        standardTile("off", "device.modeOff", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"Security Off", action:"modeOff", icon:"st.security.alarm.off"
        }
        standardTile("away", "device.modeAway", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"Away Mode", action:"modeAway", icon:"st.security.alarm.on"
        }
        standardTile("night", "device.modeNight", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"Night Mode", action:"modeOff", icon:"st.security.alarm.partial"
        }
        standardTile("day", "device.modeDay", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"Day Mode", action:"modeOff", icon:"st.security.alarm.partial"
        }
        standardTile("vacation", "device.modeVacation", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"Vacation Mode", action:"modeOff", icon:"st.security.alarm.on"
        }

        standardTile("maker", "device.maker", height: 2, width: 2, decoration: "flat") {
            state("on", label:'On', nextState: "pending", action:"makerToggle", icon:"st.unknown.zwave.static-controller",backgroundColor:"#00ff00")
            state("off", label:'Off', nextState: "pending", action:"makerToggle", icon:"st.unknown.zwave.static-controller",backgroundColor:"#ffffff")
            state("pending", label:'waiting',action:"makerToggle", icon:"st.unknown.zwave.static-controller",backgroundColor:"#ff0000")
        }
		main "AlarmState"
		details ("AlarmState", "AlarmMode", "off", "away", "night" , "day", "vacation", "refresh")
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
        	value = value.toInteger()
            def code = ["noAlarm", "intruderAlarm", "duress", "phoneLineTrouble"]as String[]
        	log.debug "->processEvent: State Update"
        	if (code[value]) {
                log.debug "->processEvent: Setting alarmstate to ${code[value]}"
    		    sendEvent(name: "AlarmState", value: code[value], descriptionText: "Alarm is ${code[value]}")
            } else {
                log.debug "->processEvent: Failed to setting alarmstate to ${value}, no such entry"
            }
    	} else if (item=="Maker") {
        	if (value=="0") {
            	updateDataValue("maker","Off")
            	value="off"
            } else {
            	value="on"
                updateDataValue("maker","On")
            }
            log.debug "->processEvent: Setting Maker to ${value}"
            sendEvent(name: "maker", value: value, descriptionText: "Maker is ${value}")
        } else if (item=="SecurityMode") {
            if (value=="O") {
                value = "off"
            } else if (value=="A") {
                value = "away"
            } else if (value=="N") {
                value = "night"
            } else if (value=="D") {
                value = "day"
            } else if (value=="V") {
                value = "vacation"
            } else if (value=="W") {
            	value = "armOK"
            } else if (value=="X") {
            	value = "armWait"
            } else if (value=="Y") {
            	value = "entryDelay"
            } else if (value=="Z") {
            	value = "exitDelay"
            }
            log.debug "->processEvent: Setting Alarm Mode  to ${value}"
            sendEvent(name: "AlarmMode", value: value, descriptionText: "Alarm Mode is ${value}")
        } else {
           log.debug "->processEvent: no trap for ${item}"
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
    sendEvent(name: "AlarmMode", value: "offline", descriptionText: "The device is offline")
    sendEvent(name: "AlarmState", value: "offline", descriptionText: "The device is offline")
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

void on() {
    doAction("SetSecurityMode",[NewSecurityModeValue:"RA"+alarmCode])
}
void off() {
    doAction("SetSecurityMode",[NewSecurityModeValue:"RO"+alarmCode])
}
void modeOff() {
	 doAction("SetSecurityMode",[NewSecurityModeValue:"RO"+alarmCode])
}
void modeDay() {
	 doAction("SetSecurityMode",[NewSecurityModeValue:"RD"+alarmCode])
}
void modeAway() {
	doAction("SetSecurityMode",[NewSecurityModeValue:"RA"+alarmCode])
}
void modeNight() {
	doAction("SetSecurityMode",[NewSecurityModeValue:"RN"+alarmCode])
}
void modeVacation() {
	doAction("SetSecurityMode",[NewSecurityModeValue:"RV"+alarmCode])
}

void addResponse() {
	parent.addResponse()
}

void executeResponse(responseCode) {
	
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

void refresh() {
	log.debug "Manual Subscription renewal"
    renewSubscription() 
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
        urn:     "urn:www.cytech.com:service:alarm:1",
        action:  action,
        body:    body,
        headers: [Host:getDataValue("ip") + ":" + getDataValue("port"), CONNECTION: "close"]
    )
    //return result
    sendHubCommand(result)
}
void processResponse(response) {
	log.debug "->processResponse"
    doAction("SetRunResponse",[NewRunResponseValue:response])
}
