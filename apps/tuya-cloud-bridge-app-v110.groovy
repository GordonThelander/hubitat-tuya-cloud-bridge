/**
 * Tuya Cloud Bridge for Hubitat
 *
 * Author: Gordon Thelander
 * Version: 1.1.0
 *
 * Purpose:
 *   Clean Hubitat parent app for direct Tuya Cloud integration.
 *
 * Primary use case:
 *   Hubitat action device -> Tuya Cloud Bridge -> Tuya OpenAPI -> Tuya direct device command.
 *
 * Notes:
 *   - Scene support is intentionally secondary because Tuya can gate scene APIs behind expired resource packs.
 *   - Direct device command testing uses the standard Tuya Device Control APIs.
 *   - Do not share logs without redacting Access ID, Access Secret, tokens, signatures, device IDs, home IDs and raw responses.
 */

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

definition(
    name: "Tuya Cloud Bridge",
    namespace: "gt",
    author: "Gordon Thelander",
    description: "Hubitat to Tuya Cloud bridge for direct device commands and optional scene actions.",
    category: "Convenience",
    singleInstance: true,
    iconUrl: "",
    iconX2Url: "",
    importUrl: ""
)

preferences {
    page(name: "mainPage")
    page(name: "tokenTestPage")
    page(name: "deviceListPage")
    page(name: "deviceInspectPage")
    page(name: "deviceCommandTestPage")
    page(name: "deviceCommandTestResultPage")
    page(name: "createManualCommandPage")
    page(name: "createManualCommandResultPage")
    page(name: "sceneListPage")
    page(name: "createSceneChildrenPage")
    page(name: "createSceneChildrenResultPage")
    page(name: "rawScenePage")
}

def installed() { initialiseApp() }
def updated() { initialiseApp() }

def initialiseApp() {
    logInfo("Initialised")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Tuya Cloud Bridge", install: true, uninstall: true) {
        section("Tuya Cloud credentials") {
            input "tuyaEndpoint", "enum",
                title: "Tuya Cloud endpoint",
                required: true,
                submitOnChange: true,
                defaultValue: "https://openapi.tuyaeu.com",
                options: [
                    "https://openapi.tuyaeu.com": "Central Europe - https://openapi.tuyaeu.com",
                    "https://openapi-weaz.tuyaeu.com": "Western Europe - https://openapi-weaz.tuyaeu.com",
                    "https://openapi.tuyaus.com": "Western America - https://openapi.tuyaus.com",
                    "https://openapi-ueaz.tuyaus.com": "Eastern America - https://openapi-ueaz.tuyaus.com",
                    "https://openapi-sg.iotbing.com": "Singapore / APAC - https://openapi-sg.iotbing.com",
                    "https://openapi.tuyain.com": "India - https://openapi.tuyain.com",
                    "https://openapi.tuyacn.com": "China - https://openapi.tuyacn.com"
                ]

            input "tuyaAccessId", "text",
                title: "Tuya Access ID / Client ID",
                required: true

            input "tuyaAccessSecret", "password",
                title: "Tuya Access Secret / Client Secret",
                required: true

            input "tuyaHomeId", "text",
                title: "Tuya numeric Home ID / Group ID",
                description: "Optional for direct device commands, required for scene actions.",
                required: false
        }

        section("Options") {
            input "autoOffSeconds", "number",
                title: "Auto-reset child switch after trigger, seconds",
                required: true,
                defaultValue: 1

            input "debugLog", "bool",
                title: "Enable debug logging - do not share logs without redaction",
                required: false,
                defaultValue: false,
                submitOnChange: true
        }

        section("Direct device-command workflow - use this first") {
            href "tokenTestPage",
                title: "1. Test Tuya token",
                description: "Verify endpoint, Access ID, Access Secret and signing."

            href "deviceListPage",
                title: "2. List Tuya devices",
                description: "Find the Tuya Device ID for the IR blaster or target device."

            href "deviceInspectPage",
                title: "3. Inspect a Tuya device",
                description: "Get status, functions and specifications for a Device ID."

            href "deviceCommandTestPage",
                title: "4. Test direct Tuya device command",
                description: "Send a test command to a Tuya Device ID."

            href "createManualCommandPage",
                title: "5. Create manual direct-command child",
                description: "Create a Hubitat child switch for a known working Tuya command."
        }

        section("Optional scene workflow - may be blocked by Tuya resource pack") {
            href "sceneListPage",
                title: "List Tuya scenes",
                description: "Optional. This may fail if Tuya Scene Linkage is expired."

            href "createSceneChildrenPage",
                title: "Create Hubitat child devices for Tuya scenes",
                description: "Optional. Requires scene-list permission."

            href "rawScenePage",
                title: "Show raw scene response",
                description: "Diagnostics only. Redact before sharing."
        }

        section("Current child devices") {
            def children = getChildDevices()
            if (!children) {
                paragraph "No child action devices have been created yet."
            } else {
                paragraph "<pre>${htmlEscape(children.collect { "${it.displayName} - ${it.deviceNetworkId}" }.join('\n'))}</pre>"
            }
        }
    }
}

/* --------------------------
 * UI pages
 */

def tokenTestPage() {
    def result = getTuyaToken(false)
    dynamicPage(name: "tokenTestPage", title: "Tuya Token Test", install: false, nextPage: "mainPage") {
        section("Result") {
            if (result.ok) {
                paragraph "Token request succeeded."
                paragraph "Token expires in approximately ${result.expireTime ?: 'unknown'} seconds."
            } else {
                paragraph "Token request failed."
                paragraph "${result.error ?: 'Unknown token error'}"
                if (result.raw) paragraph "<pre>${htmlEscape(JsonOutput.prettyPrint(JsonOutput.toJson(result.raw)))}</pre>"
            }
        }
    }
}

def deviceListPage() {
    def result = listDevices()
    dynamicPage(name: "deviceListPage", title: "Tuya Devices", install: false, nextPage: "mainPage") {
        section("Device list") {
            if (!result.ok) {
                paragraph "Unable to list Tuya devices."
                paragraph "${result.error ?: 'Unknown device-list error'}"
                if (result.raw) paragraph "<pre>${htmlEscape(JsonOutput.prettyPrint(JsonOutput.toJson(result.raw)))}</pre>"
            } else {
                def devices = result.devices ?: []
                paragraph "Devices found: ${devices.size()}"
                if (devices.size() == 0) {
                    paragraph "No devices returned. Confirm your Smart Life/Tuya app account is linked to this cloud project and that IoT Core/Device Control APIs are authorised."
                } else {
                    paragraph "<pre>${htmlEscape(formatDeviceList(devices))}</pre>"
                }
            }
        }
    }
}

def deviceInspectPage() {
    dynamicPage(name: "deviceInspectPage", title: "Inspect Tuya Device", install: false, nextPage: "mainPage") {
        section("Device to inspect") {
            input "inspectDeviceId", "text",
                title: "Tuya Device ID",
                required: true,
                submitOnChange: true
        }

        if (inspectDeviceId) {
            section("Device details") {
                def result = inspectDevice(inspectDeviceId.toString())
                if (!result.ok) {
                    paragraph "Unable to inspect device."
                    paragraph "${result.error ?: 'Unknown inspect error'}"
                    if (result.raw) paragraph "<pre>${htmlEscape(JsonOutput.prettyPrint(JsonOutput.toJson(result.raw)))}</pre>"
                } else {
                    paragraph "<pre>${htmlEscape(JsonOutput.prettyPrint(JsonOutput.toJson(result.data)))}</pre>"
                }
            }
        }
    }
}

def deviceCommandTestPage() {
    dynamicPage(name: "deviceCommandTestPage", title: "Test Direct Tuya Device Command", install: false, nextPage: "deviceCommandTestResultPage") {
        section("Command") {
            paragraph "Use this only after inspecting the device and confirming a valid command code/value."

            input "testDeviceId", "text",
                title: "Tuya Device ID",
                required: true

            input "testCommandCode", "text",
                title: "Command code",
                required: true,
                description: "Example: switch, switch_1, mode"

            input "testCommandValueType", "enum",
                title: "Value type",
                required: true,
                defaultValue: "boolean",
                options: [
                    "boolean": "Boolean",
                    "number": "Number",
                    "string": "String",
                    "json": "Raw JSON value/object"
                ]

            input "testCommandValue", "text",
                title: "Command value",
                required: true,
                description: "Examples: true, false, 1, heat, {\"foo\":\"bar\"}"
        }
    }
}

def deviceCommandTestResultPage() {
    def value = coerceManualValue(testCommandValue, testCommandValueType)
    def command = [code: testCommandCode.toString(), value: value]
    def result = sendDeviceCommand(testDeviceId.toString(), JsonOutput.toJson([command]))

    dynamicPage(name: "deviceCommandTestResultPage", title: "Direct Command Test Result", install: false, nextPage: "mainPage") {
        section("Result") {
            paragraph "Command sent:"
            paragraph "<pre>${htmlEscape(JsonOutput.prettyPrint(JsonOutput.toJson(command)))}</pre>"

            if (result.ok) {
                paragraph "Tuya command request succeeded."
                paragraph "<pre>${htmlEscape(JsonOutput.prettyPrint(JsonOutput.toJson(result.parsed ?: result.raw)))}</pre>"
            } else {
                paragraph "Tuya command request failed."
                paragraph "${result.error ?: 'Unknown command error'}"
                if (result.raw) paragraph "<pre>${htmlEscape(JsonOutput.prettyPrint(JsonOutput.toJson(result.raw)))}</pre>"
            }
        }
    }
}

def createManualCommandPage() {
    dynamicPage(name: "createManualCommandPage", title: "Create Manual Direct-Command Child", install: false, nextPage: "createManualCommandResultPage") {
        section("Manual device command") {
            paragraph "Create this only after the direct command test works."

            input "manualActionName", "text",
                title: "Hubitat child device name",
                required: true,
                description: "Example: Tuya Heater On"

            input "manualDeviceId", "text",
                title: "Tuya Device ID",
                required: true

            input "manualCommandCode", "text",
                title: "Tuya command code",
                required: true

            input "manualCommandValueType", "enum",
                title: "Value type",
                required: true,
                defaultValue: "boolean",
                options: [
                    "boolean": "Boolean",
                    "number": "Number",
                    "string": "String",
                    "json": "Raw JSON value/object"
                ]

            input "manualCommandValue", "text",
                title: "Command value",
                required: true
        }
    }
}

def createManualCommandResultPage() {
    def value = coerceManualValue(manualCommandValue, manualCommandValueType)
    def command = [code: manualCommandCode.toString(), value: value]
    def child = createOrUpdateActionChild("device", manualActionName.toString(), [
        actionType: "deviceCommand",
        deviceId: manualDeviceId.toString(),
        commandJson: JsonOutput.toJson([command])
    ])

    ["manualActionName", "manualDeviceId", "manualCommandCode", "manualCommandValueType", "manualCommandValue"].each { app?.removeSetting(it) }

    dynamicPage(name: "createManualCommandResultPage", title: "Create Manual Command Result", install: false, nextPage: "mainPage") {
        section("Result") {
            paragraph "Created/updated child device: ${child?.displayName}"
            paragraph "Command: <pre>${htmlEscape(JsonOutput.prettyPrint(JsonOutput.toJson(command)))}</pre>"
        }
    }
}

def sceneListPage() {
    def result = listScenes()
    dynamicPage(name: "sceneListPage", title: "Tuya Scenes", install: false, nextPage: "mainPage") {
        section("Scene list") { renderSceneResult(result) }
    }
}

def rawScenePage() {
    def result = listScenes()
    dynamicPage(name: "rawScenePage", title: "Raw Tuya Scene Response", install: false, nextPage: "mainPage") {
        section("Raw response") {
            paragraph "Raw response below. Redact before sharing."
            paragraph "<pre>${htmlEscape(JsonOutput.prettyPrint(JsonOutput.toJson(result.raw ?: result)))}</pre>"
        }
    }
}

def createSceneChildrenPage() {
    def result = listScenes()
    def sceneOptions = [:]

    if (result.ok) {
        result.scenes.each { s ->
            def sceneId = getSceneId(s)
            def sceneName = getSceneName(s)
            if (sceneId) sceneOptions[sceneId] = "${sceneName} (${sceneId})"
        }
    }

    dynamicPage(name: "createSceneChildrenPage", title: "Create Tuya Scene Children", install: false, nextPage: "createSceneChildrenResultPage") {
        section("Scenes") {
            if (!result.ok) {
                paragraph "Unable to retrieve scenes."
                paragraph "${result.error ?: 'Unknown scene-list error'}"
            } else if (sceneOptions.size() == 0) {
                paragraph "No scenes found for Home ID ${maskValue(settings.tuyaHomeId)}."
            } else {
                input "selectedSceneIds", "enum",
                    title: "Select Tuya scenes to expose in Hubitat",
                    required: true,
                    multiple: true,
                    options: sceneOptions
            }
        }
    }
}

def createSceneChildrenResultPage() {
    def selected = normaliseSelection(selectedSceneIds)
    def result = listScenes()
    def sceneById = [:]
    if (result.ok) {
        result.scenes.each { s ->
            def sid = getSceneId(s)
            if (sid) sceneById[sid] = s
        }
    }

    def lines = []
    selected.each { sid ->
        def scene = sceneById[sid]
        if (!scene) {
            lines << "Scene ${sid} not found in latest Tuya scene list."
        } else {
            def child = createOrUpdateActionChild("scene", getSceneName(scene), [
                homeId: settings.tuyaHomeId.toString(),
                sceneId: sid,
                actionType: "scene"
            ])
            lines << "Created/updated scene child: ${child?.displayName}"
        }
    }

    app?.removeSetting("selectedSceneIds")

    dynamicPage(name: "createSceneChildrenResultPage", title: "Create Scene Children Result", install: false, nextPage: "mainPage") {
        section("Result") { paragraph "<pre>${htmlEscape(lines.join('\n'))}</pre>" }
    }
}

/* --------------------------
 * Child driver callbacks
 */

void componentOn(childDevice) { triggerActionChild(childDevice) }
void componentPush(childDevice, buttonNumber = 1) { triggerActionChild(childDevice) }
void componentRefresh(childDevice) { logInfo("Refresh requested for ${childDevice.displayName}") }
void componentOff(childDevice) { childDevice?.sendEvent(name: "switch", value: "off", isStateChange: true) }

void triggerActionChild(childDevice) {
    def actionType = childDevice.getDataValue("actionType")

    childDevice.sendEvent(name: "switch", value: "on", isStateChange: true)
    childDevice.sendEvent(name: "lastTriggered", value: new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone), isStateChange: true)

    Map result
    if (actionType == "scene") {
        result = triggerScene(childDevice.getDataValue("homeId"), childDevice.getDataValue("sceneId"))
    } else if (actionType == "deviceCommand") {
        result = sendDeviceCommand(childDevice.getDataValue("deviceId"), childDevice.getDataValue("commandJson"))
    } else {
        result = [ok: false, error: "Unknown child action type: ${actionType}"]
    }

    if (result.ok) {
        childDevice.sendEvent(name: "lastResult", value: "success", isStateChange: true)
        logInfo("Triggered ${childDevice.displayName}")
    } else {
        childDevice.sendEvent(name: "lastResult", value: "error: ${result.error}", isStateChange: true)
        log.warn "Tuya action failed for ${childDevice.displayName}: ${result.error}"
    }

    Integer seconds = (settings.autoOffSeconds ?: 1) as Integer
    if (seconds >= 0) runIn(seconds, "autoOffChild", [data: [dni: childDevice.deviceNetworkId]])
}

void autoOffChild(Map data) {
    getChildDevice(data?.dni)?.sendEvent(name: "switch", value: "off", isStateChange: true)
}

/* --------------------------
 * Direct device APIs
 */

private Map listDevices() {
    def token = getTuyaToken(true)
    if (!token.ok) return [ok: false, error: "Token failure: ${token.error}", raw: token.raw]

    // Broad project-level device query. This avoids user UID lookup and is usually available under IoT Core.
    def resp = tuyaRequest("GET", "/v1.0/devices", "", token.accessToken)
    if (!resp.ok) return [ok: false, error: resp.error, raw: resp.raw]

    def parsed = resp.parsed
    def result = parsed?.result
    def devices = []

    if (result instanceof List) {
        devices = result
    } else if (result instanceof Map) {
        devices = result.devices ?: result.list ?: result.data ?: result.rows ?: []
    }

    return [ok: true, devices: devices, raw: parsed]
}

private Map inspectDevice(String deviceId) {
    def token = getTuyaToken(true)
    if (!token.ok) return [ok: false, error: "Token failure: ${token.error}", raw: token.raw]

    def out = [:]

    out.details = tuyaRequest("GET", "/v1.0/devices/${deviceId}", "", token.accessToken).parsed
    out.status = tuyaRequest("GET", "/v1.0/devices/${deviceId}/status", "", token.accessToken).parsed
    out.functions = tuyaRequest("GET", "/v1.0/devices/${deviceId}/functions", "", token.accessToken).parsed
    out.specifications = tuyaRequest("GET", "/v1.0/devices/${deviceId}/specifications", "", token.accessToken).parsed
    out.iot03Specification = tuyaRequest("GET", "/v1.0/iot-03/devices/${deviceId}/specification", "", token.accessToken).parsed

    return [ok: true, data: out]
}

private Map sendDeviceCommand(String deviceId, String commandJson) {
    if (!deviceId || !commandJson) return [ok: false, error: "Missing deviceId or commandJson"]

    def commands
    try {
        commands = new JsonSlurper().parseText(commandJson)
    } catch (e) {
        return [ok: false, error: "Invalid command JSON: ${e}"]
    }

    if (!(commands instanceof List)) commands = [commands]

    def token = getTuyaToken(true)
    if (!token.ok) return [ok: false, error: "Token failure: ${token.error}", raw: token.raw]

    def body = JsonOutput.toJson([commands: commands])
    return tuyaRequest("POST", "/v1.0/devices/${deviceId}/commands", body, token.accessToken)
}

private String formatDeviceList(List devices) {
    def lines = ""
    devices.eachWithIndex { d, idx ->
        lines += "${idx + 1}. ${d.name ?: d.device_name ?: d.product_name ?: 'Unnamed device'}\n"
        lines += "   Device ID: ${d.id ?: d.device_id ?: d.dev_id ?: 'unknown'}\n"
        if (d.category) lines += "   Category: ${d.category}\n"
        if (d.product_name) lines += "   Product: ${d.product_name}\n"
        if (d.online != null) lines += "   Online: ${d.online}\n"
        if (d.uuid) lines += "   UUID: ${d.uuid}\n"
        lines += "\n"
    }
    return lines
}

/* --------------------------
 * Scene APIs
 */

private Map triggerScene(String homeId, String sceneId) {
    if (!homeId || !sceneId) return [ok: false, error: "Missing homeId or sceneId"]
    def token = getTuyaToken(true)
    if (!token.ok) return [ok: false, error: "Token failure: ${token.error}", raw: token.raw]
    return tuyaRequest("POST", "/v1.0/homes/${homeId}/scenes/${sceneId}/trigger", "", token.accessToken)
}

private Map listScenes() {
    if (!settings.tuyaHomeId) return [ok: false, error: "Home ID is required for scene listing."]
    def token = getTuyaToken(true)
    if (!token.ok) return [ok: false, error: "Token failure: ${token.error}", raw: token.raw]

    def resp = tuyaRequest("GET", "/v1.1/homes/${settings.tuyaHomeId}/scenes", "", token.accessToken)
    if (!resp.ok) return [ok: false, error: resp.error ?: "Scene API request failed", raw: resp.raw]

    return [ok: true, scenes: extractScenes(resp.parsed), raw: resp.parsed]
}

private List extractScenes(parsed) {
    def result = parsed?.result
    if (result instanceof List) return result
    if (result instanceof Map) {
        if (result.scenes instanceof List) return result.scenes
        if (result.list instanceof List) return result.list
        if (result.data instanceof List) return result.data
        if (result.records instanceof List) return result.records
        return [result]
    }
    return []
}

private void renderSceneResult(Map result) {
    if (!result.ok) {
        paragraph "Unable to list Tuya scenes."
        paragraph "${result.error ?: 'Unknown error'}"
        if (result.raw) paragraph "<pre>${htmlEscape(JsonOutput.prettyPrint(JsonOutput.toJson(result.raw)))}</pre>"
        return
    }

    def scenes = result.scenes ?: []
    paragraph "Scenes found: ${scenes.size()}"
    if (scenes.size() == 0) {
        paragraph "No scenes returned. Confirm Home ID, data centre and scene API permissions."
        return
    }

    def lines = ""
    scenes.eachWithIndex { scene, idx ->
        lines += "${idx + 1}. ${getSceneName(scene)}\n"
        lines += "   Scene ID: ${getSceneId(scene)}\n\n"
    }
    paragraph "<pre>${htmlEscape(lines)}</pre>"
}

private String getSceneName(Map scene) {
    return (scene.name ?: scene.scene_name ?: scene.sceneName ?: scene.title ?: "Unnamed Tuya scene").toString()
}

private String getSceneId(Map scene) {
    return (scene.scene_id ?: scene.sceneId ?: scene.id ?: "").toString()
}

/* --------------------------
 * Child creation
 */

private def createOrUpdateActionChild(String type, String label, Map dataValues) {
    String rawId = dataValues.sceneId ?: dataValues.deviceId ?: label
    String dni = "tuya-cloud-${type}-${rawId}".replaceAll("[^A-Za-z0-9._:-]", "_")

    def child = getChildDevice(dni)
    if (!child) {
        child = addChildDevice("gt", "Tuya Cloud Action Child", dni, [
            name: label,
            label: label,
            isComponent: false
        ])
    }

    dataValues.each { k, v -> child.updateDataValue(k.toString(), v?.toString() ?: "") }
    child.sendEvent(name: "switch", value: "off", isStateChange: false)
    child.sendEvent(name: "lastResult", value: "created/updated", isStateChange: true)
    return child
}

/* --------------------------
 * Tuya auth/signing
 */

private Map getTuyaToken(Boolean allowCache = true) {
    if (allowCache && state.tuyaAccessToken && state.tuyaTokenExpiryMs) {
        Long nowMs = now()
        if (nowMs < (state.tuyaTokenExpiryMs as Long)) {
            return [ok: true, accessToken: state.tuyaAccessToken, expireTime: Math.max(0, (((state.tuyaTokenExpiryMs as Long) - nowMs) / 1000).toInteger())]
        }
    }

    def resp = tuyaRequest("GET", "/v1.0/token?grant_type=1", "", null)
    if (!resp.ok) return [ok: false, error: resp.error ?: "Token HTTP request failed", raw: resp.raw]

    def parsed = resp.parsed
    if (!(parsed instanceof Map)) return [ok: false, error: "Token response was not a JSON object.", raw: resp.raw]
    if (parsed.success == false) return [ok: false, error: "Tuya token API failed. code=${parsed.code}, msg=${parsed.msg}", raw: parsed]

    def token = parsed?.result?.access_token ?: parsed?.result?.accessToken
    def expireTime = parsed?.result?.expire_time ?: parsed?.result?.expireTime ?: 7200
    if (!token) return [ok: false, error: "Tuya token response did not contain access_token.", raw: parsed]

    state.tuyaAccessToken = token.toString()
    state.tuyaTokenExpiryMs = now() + Math.max(60, ((expireTime as Integer) - 60)) * 1000

    return [ok: true, accessToken: state.tuyaAccessToken, expireTime: expireTime]
}

private Map tuyaRequest(String method, String pathWithQuery, String bodyText, String accessToken) {
    if (!settings.tuyaEndpoint || !settings.tuyaAccessId || !settings.tuyaAccessSecret) {
        return [ok: false, error: "Missing Tuya endpoint, Access ID or Access Secret."]
    }

    String endpoint = settings.tuyaEndpoint.toString().trim()
    if (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1)

    String path = normalisePath(pathWithQuery)
    String methodUpper = method.toUpperCase()
    String body = bodyText ?: ""
    String t = now().toString()
    String nonce = randomNonce(16)

    String contentHash = sha256Hex(body)
    String stringToSign = methodUpper + "\n" + contentHash + "\n" + "\n" + path

    String signPayload = accessToken ?
        settings.tuyaAccessId.toString() + accessToken.toString() + t + nonce + stringToSign :
        settings.tuyaAccessId.toString() + t + nonce + stringToSign

    String sign = hmacSha256Upper(signPayload, settings.tuyaAccessSecret.toString())

    Map headers = [
        "client_id": settings.tuyaAccessId.toString(),
        "sign": sign,
        "t": t,
        "sign_method": "HMAC-SHA256",
        "nonce": nonce
    ]
    if (accessToken) headers["access_token"] = accessToken.toString()

    Map params = [
        uri: endpoint + path,
        headers: headers,
        contentType: "application/json",
        requestContentType: "application/json",
        timeout: 25
    ]
    if (body.length() > 0) params.body = body

    logDebug("Tuya request method=${methodUpper}, path=${path}, endpoint=${endpoint}, tokenPresent=${accessToken ? true : false}")

    def parsed = null
    def raw = null
    try {
        if (methodUpper == "GET") {
            httpGet(params) { resp ->
                parsed = normaliseJson(resp.data)
                raw = resp.data?.toString()
            }
        } else if (methodUpper == "POST") {
            httpPost(params) { resp ->
                parsed = normaliseJson(resp.data)
                raw = resp.data?.toString()
            }
        } else {
            return [ok: false, error: "Unsupported HTTP method ${methodUpper}"]
        }

        logDebug("Tuya response raw=${raw}")
        if (parsed instanceof Map && parsed.success == false) {
            return [ok: false, error: "Tuya API failed. code=${parsed.code}, msg=${parsed.msg}", raw: parsed, parsed: parsed]
        }
        return [ok: true, raw: raw, parsed: parsed]
    } catch (e) {
        return [ok: false, error: "${e}", raw: raw, parsed: parsed]
    }
}

/* --------------------------
 * Helpers
 */

private Object normaliseJson(raw) {
    if (raw == null) return null
    if (raw instanceof Map || raw instanceof List) return raw
    try {
        return new JsonSlurper().parseText(raw.toString())
    } catch (ignored) {
        return raw
    }
}

private String normalisePath(String path) {
    if (!path.startsWith("/")) return "/" + path
    return path
}

private List<String> normaliseSelection(value) {
    if (value == null) return []
    if (value instanceof List) return value.collect { it.toString() }
    return [value.toString()]
}

private Object coerceManualValue(String raw, String type) {
    if (type == "boolean") return raw?.toString()?.toLowerCase() == "true"
    if (type == "number") {
        def s = raw?.toString()
        return s?.contains(".") ? new BigDecimal(s) : Integer.parseInt(s)
    }
    if (type == "json") {
        return new JsonSlurper().parseText(raw?.toString() ?: "null")
    }
    return raw?.toString()
}

private String randomNonce(int length) {
    def chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return new Random().with { (1..length).collect { chars[nextInt(chars.length())] }.join() }
}

private String sha256Hex(String value) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256")
    byte[] hash = digest.digest((value ?: "").getBytes("UTF-8"))
    return hash.collect { String.format("%02x", it & 0xff) }.join()
}

private String hmacSha256Upper(String value, String secret) {
    Mac mac = Mac.getInstance("HmacSHA256")
    SecretKeySpec key = new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256")
    mac.init(key)
    byte[] rawHmac = mac.doFinal(value.getBytes("UTF-8"))
    return rawHmac.collect { String.format("%02X", it & 0xff) }.join()
}

private String htmlEscape(value) {
    if (value == null) return ""
    return value.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

private String maskValue(value) {
    def s = value?.toString() ?: ""
    if (s.length() <= 4) return "****"
    return s.substring(0, 2) + "****" + s.substring(s.length() - 2)
}

private void logInfo(msg) { log.info "Tuya Cloud Bridge: ${msg}" }
private void logDebug(msg) { if (settings.debugLog) log.debug "Tuya Cloud Bridge: ${msg}" }
