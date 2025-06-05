package com.penumbraos.bridge.util

import android.util.Log
import com.google.protobuf.MessageLite
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun writeMessage(message: MessageLite, output: OutputStream) {
    val bytes = message.toByteArray()
    val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(bytes.size)
    buffer.rewind()
    val lengthBytes = ByteArray(4)
    buffer.get(lengthBytes)
    Log.w("TestMessage", "DEBUG: Writing message length=${bytes.size}, bytes=${lengthBytes.joinToString { it.toUByte().toString() }}")
    output.write(lengthBytes)
    output.write(bytes)
    output.flush()
}