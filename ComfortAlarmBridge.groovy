/**
 *
 * UPNP Bridge Interface
 * Copyright Matt Brain (matt.brain@gmail.com)
 * https://github.com/mattbrain/Cytech-Comfort
 *
 *
 *
 */
 
metadata {
	definition (name: "ComfortAlarm Bridge", namespace: "mattbrain", author: "Matt Brain") {
 
 		capability "Sensor"
        capability "Polling"
        capability "Refresh"
 
        attribute "throughput", "number"
		attribute "status", "enum", ["online", "offline", "unknown"]
   
        command "subscribe"
        command "resubscribe"
        command "unsubscribe"
        command "addchild"
        command "setbridgeaddress"
        command "addResponse"
	}

	simulator {
		// TODO: define status and reply messages here
	}

	tiles (scale: 2) {
 	
        valueTile ("throughput", "device.throughput", decoration: "flat", height: 2, width: 6, inactiveLabel: false) {
             	 state ("EPM", label: 'EPM: ${currentValue}', unit:"EPM",
                 	backgroundColors:[
                    	[value:0, color: "#ffffff"],
                        [value:1, color: "#44b621"],
                        [value:75, color: "#f1d801"],
                        [value:150, color: "#ff0000"]
                    ]
              	)
 		}
        standardTile("refresh", "device.switch", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        standardTile("addResponse", "device.addResponse", inactiveLabel: false, height: 2,width: 3, decoration: "flat") {
        	state "default", label:"Add Response Device", action:"addResponse", icon:"st.unknown.zwave.remote-controller"
		}
		main ('throughput')
		details ('throughput','refresh', 'addResponse')
	}
}


def initialize(){
	log.debug "Initialising"
    schedule("5 0/1 * * * ?",updateCounter)
}

void refresh() {
	parent.renewSubscriptions()
	updateCounter()
	schedule("5 0/1 * * * ?",updateCounter)    
}


void renewSubscription() {
	log.debug "Subscription Renewal"
    // nothing to do here for Comfort Bridge
}



// parse events into attributes
def parse(String description) {
	log.debug "Parsing ${description}"

	def msg = parseLanMessage(description)
	def headerString = msg.header
    def bodyString = msg.body
    log.debug "Parsed: ${headerString}, ${bodyString}"
    def notifyString = getChildFromHeader(headerString)
    def sid = getSIDFromHeader(headerString)
    
    if (notifyString) {
    	// Is this a notify message, and who is it for
    	log.debug "Notify for ${notifyString}, ${sid}"
        parseNotification(notifyString,sid,msg) 
    } else {
    	// shouldn't get here - but it might happen
        log.debug "message received but not notification"
	}
    //runEvery5Minutes(updateCounter)
    if (state.hitcounter) {
    	state.hitcounter = state.hitcounter + 1
    } else {
    	state.hitcounter = 1
    }
}

void parseNotification(notifyString, sid, msg) {
	log.debug "->parseNotification ${notifyString}"
	def bodyString = msg.body
	def body = new XmlSlurper().parseText(bodyString)
    body.property.children().each {
    	log.debug "Incoming notification ${it.name()}:${it.text()}"
        parent.processEvent(notifyString, sid, it.name(),it.text())
	}
}

def poll() {
	log.debug "Poll recieved"
	updateCounter()
}

def updateCounter() {
	if (!state.lastdatatime) {
    	state.lastdatatime = now()
        state.hitcounter = 0
        sendEvent(name: "throughput", value: 0)
        log.debug "first poll, no data available"
        return
    }
    def deltatime = (now() - state.lastdatatime) / 1000
    log.debug "time since last throughput calc ${deltatime}"
    def throughput = (state.hitcounter / deltatime) * 60
 	state.lastdatatime = now()
	state.hitcounter = 0
    sendEvent(name: "throughput", value: throughput)
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

void addResponse() {
	parent.addResponse()
}