# hubitat-tuya-cloud-bridge

A Hubitat Groovy app and child driver for triggering Tuya Cloud device actions from Hubitat automations.

## Overview

This project provides a lightweight Hubitat integration pattern for controlling selected Tuya Cloud devices from Hubitat. It is intended for use cases where a direct local integration is not available, but Tuya Cloud device actions can still be triggered through the Tuya API.

The integration consists of:

| File | Purpose |
|---|---|
| `tuya-cloud-bridge-app-v110.groovy` | Parent Hubitat app that stores Tuya Cloud credentials, discovers/configures actions, and creates child devices |
| `tuya-cloud-action-child-driver-v101.groovy` | Child driver used by Hubitat to expose Tuya actions as switch/button-style devices |

## Intended Use Case

This was built for bridging awkward Tuya-based devices into Hubitat, especially where native Hubitat support is limited or unavailable.

Example use cases:

| Use case | Description |
|---|---|
| Tuya IR blaster actions | Trigger learned Tuya IR commands from Hubitat |
| Virtual action switches | Expose Tuya Cloud actions as Hubitat child devices |
| Automation bridging | Allow Hubitat Rule Machine or dashboards to trigger Tuya device actions |

## Installation

### 1. Install the child driver first

In Hubitat:

1. Go to **Drivers Code**
2. Select **New Driver**
3. Paste the contents of `tuya-cloud-action-child-driver-v101.groovy`
4. Click **Save**

### 2. Install the parent app

In Hubitat:

1. Go to **Apps Code**
2. Select **New App**
3. Paste the contents of `tuya-cloud-bridge-app-v110.groovy`
4. Click **Save**

### 3. Add the app

In Hubitat:

1. Go to **Apps**
2. Select **Add User App**
3. Choose **Tuya Cloud Bridge**
4. Enter your Tuya Cloud project credentials
5. Configure the Tuya device/action mappings
6. Create the required child devices

## Requirements

| Requirement | Notes |
|---|---|
| Hubitat Elevation hub | Required |
| Tuya IoT Cloud project | Required |
| Tuya Access ID / Client ID | Required |
| Tuya Access Secret | Required |
| Tuya device ID | Required |
| Internet access from Hubitat | Required because this is Tuya Cloud based |

## Limitations

| Limitation | Explanation |
|---|---|
| Cloud dependent | This is not a local Tuya LAN integration |
| Tuya API access required | The integration depends on the Tuya IoT Cloud platform being active and accessible |
| Device support varies | Some Tuya devices expose clean commands, others do not |
| IR blasters can be awkward | Learned IR commands may not expose as normal device functions |
| No guarantee of long-term API stability | Tuya may change API access, trial terms, or cloud service requirements |


