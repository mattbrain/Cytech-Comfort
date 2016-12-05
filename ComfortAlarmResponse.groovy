/**
 *
 * UPNP Comfort Alarm Response Interface
 * Copyright Matt Brain (matt.brain@gmail.com)
 * https://github.com/mattbrain/Cytech-Comfort
 *
 *
 *
 */
metadata {
	definition (name: "ComfortAlarm Response", namespace: "mattbrain", author: "Matt Brain") {
    	capability "Actuator"
    	capability "Switch"
        capability "Refresh"
        capability "Sensor"
        
        attribute "currentIP", "string"
        attribute "maker", "string"
        
	}

	simulator {
		// TODO: define status and reply messages here
	}

	preferences {
    	input name: "onAction", type: "number", title: "On Action",  description: "Enter Response to launch with On action", required: true, displayDuringSetup: true
        input name: "offAction", type: "number", title: "Off Action",  description: "Enter Response to launch with Off action", required: true, displayDuringSetup: true    
	}
    
	tiles (scale: 2) {
  	
        standardTile("on", "device.switchon", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"Turn On", action:"on", icon:"st.illuminance.illuminance.light"
        }
        standardTile("off", "device.switchoff", inactiveLabel: false, height: 2, width: 2, decoration: "flat") {
            state "default", label:"Turn Off", action:"off", icon:"st.illuminance.illuminance.dark"
        }

	main "on"
	details ("on", "off")
	}
}

// parse events into attributes
def parse(String description) {
	log.debug "->parse ${description}"
    
}
 

void subscribe() {
}



def setOffline() {
  
}

void on() {
	log.debug "doing On action"
	if (onAction) {
		parent.processResponse(onAction)
    }
}

void off() {
	log.debug "doing Off action"
	if (offAction) {
    	parent.processResponse(offAction)
    }
}