/**
 * Tuya Cloud Action Child
 *
 * Author: Gordon Thelander
 * Version: 1.0.1
 *
 * Generic Hubitat child device for Tuya Cloud Bridge.
 * This driver delegates all Tuya API actions to the parent app.
 */

metadata {
    definition(
        name: "Tuya Cloud Action Child",
        namespace: "gt",
        author: "Gordon Thelander"
    ) {
        capability "Actuator"
        capability "Switch"
        capability "PushableButton"
        capability "Momentary"
        capability "Refresh"

        command "trigger"

        attribute "lastTriggered", "string"
        attribute "lastResult", "string"
    }

    preferences {
        input name: "debugLog",
            type: "bool",
            title: "Enable debug logging",
            required: false,
            defaultValue: false
    }
}

def installed() {
    sendEvent(name: "numberOfButtons", value: 1, isStateChange: false)
    sendEvent(name: "switch", value: "off", isStateChange: false)
}

def updated() {
    sendEvent(name: "numberOfButtons", value: 1, isStateChange: false)
}

def on() {
    logDebug("on()")
    parent?.componentOn(device)
}

def off() {
    logDebug("off()")
    sendEvent(name: "switch", value: "off", isStateChange: true)
    parent?.componentOff(device)
}

def push() {
    push(1)
}

def push(buttonNumber) {
    Integer button = buttonNumber ? buttonNumber as Integer : 1
    logDebug("push(${button})")
    sendEvent(name: "pushed", value: button, isStateChange: true)
    parent?.componentPush(device, button)
}

def trigger() {
    push(1)
}

def momentaryPush() {
    push(1)
}

def refresh() {
    logDebug("refresh()")
    parent?.componentRefresh(device)
}

private void logDebug(msg) {
    if (settings.debugLog) {
        log.debug "${device.displayName}: ${msg}"
    }
}
