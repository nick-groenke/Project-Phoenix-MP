@file:Suppress("unused") // Exception classes are infrastructure for future error handling

package com.devil.phoenixproject.data.ble

/**
 * BLE Exception hierarchy for granular error handling.
 * These exceptions are designed to be thrown by BLE operations throughout the stack.
 * Many are not yet used but provide the infrastructure for proper error handling.
 */

/**
 * Base exception for all Bluetooth-related errors.
 * Provides granular error handling for BLE operations.
 */
open class BluetoothException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Thrown when Bluetooth is disabled on the device.
 */
class BluetoothDisabledException(
    message: String = "Bluetooth is disabled. Please enable Bluetooth to connect.",
    cause: Throwable? = null
) : BluetoothException(message, cause)

/**
 * Thrown when BLE scanning fails to start or encounters an error.
 */
class ScanFailedException(
    message: String,
    val errorCode: Int? = null,
    cause: Throwable? = null
) : BluetoothException(message, cause) {
    companion object {
        // Common Android BLE scan error codes
        const val SCAN_FAILED_ALREADY_STARTED = 1
        const val SCAN_FAILED_APPLICATION_REGISTRATION_FAILED = 2
        const val SCAN_FAILED_INTERNAL_ERROR = 3
        const val SCAN_FAILED_FEATURE_UNSUPPORTED = 4
        const val SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES = 5
    }
}

/**
 * Thrown when a connection attempt is rejected by the device.
 */
class ConnectionRejectedException(
    message: String = "Connection rejected by device",
    cause: Throwable? = null
) : BluetoothException(message, cause)

/**
 * Thrown when an established connection is lost unexpectedly.
 */
class ConnectionLostException(
    message: String = "Connection lost",
    val wasUserInitiated: Boolean = false,
    cause: Throwable? = null
) : BluetoothException(message, cause)

/**
 * Thrown when a GATT operation fails with a specific status code.
 */
class GattStatusException(
    message: String,
    val status: Int,
    cause: Throwable? = null
) : BluetoothException(message, cause) {
    companion object {
        // Common GATT status codes
        const val GATT_SUCCESS = 0
        const val GATT_READ_NOT_PERMITTED = 2
        const val GATT_WRITE_NOT_PERMITTED = 3
        const val GATT_INSUFFICIENT_AUTHENTICATION = 5
        const val GATT_REQUEST_NOT_SUPPORTED = 6
        const val GATT_INVALID_OFFSET = 7
        const val GATT_INVALID_ATTRIBUTE_LENGTH = 13
        const val GATT_INSUFFICIENT_ENCRYPTION = 15
        const val GATT_ERROR = 133 // Common Android BLE error
        const val GATT_CONNECTION_TIMEOUT = 8
        const val GATT_FAILURE = 257
    }

    val isAuthenticationError: Boolean
        get() = status == GATT_INSUFFICIENT_AUTHENTICATION || status == GATT_INSUFFICIENT_ENCRYPTION

    val isCommonError133: Boolean
        get() = status == GATT_ERROR
}

/**
 * Thrown when a GATT request (read/write/notify) is rejected.
 */
class GattRequestRejectedException(
    message: String,
    val characteristicUuid: String? = null,
    cause: Throwable? = null
) : BluetoothException(message, cause)

/**
 * Thrown when an operation is attempted before the device is ready.
 */
class NotReadyException(
    message: String = "Device not ready for operation",
    cause: Throwable? = null
) : BluetoothException(message, cause)

/**
 * Thrown when a BLE operation times out.
 */
class BleTimeoutException(
    message: String = "BLE operation timed out",
    val operationType: String? = null,
    val timeoutMs: Long? = null,
    cause: Throwable? = null
) : BluetoothException(message, cause)

/**
 * Thrown when Bluetooth permissions are not granted.
 */
class BluetoothPermissionException(
    message: String = "Bluetooth permissions not granted",
    cause: Throwable? = null
) : BluetoothException(message, cause)

/**
 * Thrown when a required BLE characteristic is not found.
 */
class CharacteristicNotFoundException(
    message: String,
    val characteristicUuid: String? = null,
    cause: Throwable? = null
) : BluetoothException(message, cause)

/**
 * Thrown when the device name doesn't match expected Vitruvian patterns.
 */
class InvalidDeviceException(
    message: String = "Not a valid Vitruvian device",
    val deviceName: String? = null,
    cause: Throwable? = null
) : BluetoothException(message, cause)
