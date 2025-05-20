package com.penumbraos.bridge.util

import com.google.protobuf.MessageLite
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun writeMessage(message: MessageLite, output: OutputStream) {
    val bytes = message.toByteArray()
    output.write(ByteBuffer.allocate(4).putInt(bytes.size).order(ByteOrder.LITTLE_ENDIAN).array())
    output.write(bytes)
    output.flush()
}