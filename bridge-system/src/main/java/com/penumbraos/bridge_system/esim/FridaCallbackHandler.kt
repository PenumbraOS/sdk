package com.penumbraos.bridge_system.esim

/**
 * Interface for handling callbacks from Frida when CLI operations complete
 */
interface FridaCallbackHandler {
    /**
     * Called when a CLI operation completes
     * @param operationType The type of controller (e.g., "ProfileInfoControler", "DownloadControler", "EuiccLevelController")
     * @param operationName The specific operation (e.g., "onEnable", "onFinished", "onGetEid")
     * @param result The result string from the operation
     * @param isError Whether this represents an error condition
     */
    fun onFridaCallback(
        operationType: String,
        operationName: String,
        result: String,
        isError: Boolean
    )
}