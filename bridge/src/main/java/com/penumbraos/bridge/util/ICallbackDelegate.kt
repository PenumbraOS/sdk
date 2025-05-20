package com.penumbraos.bridge.util

import com.penumbraos.ipc.proxy.Ipc.ServerToClientMessage

interface ICallbackDelegate {
    fun callback(message: ServerToClientMessage)
    fun genericError(requestId: String, errorMessage: String)
}