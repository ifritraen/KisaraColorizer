package com.raen.kisaracolorizer.data.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Utility for allocating native-order Direct FloatBuffers for ONNX Tensor operations.
 */
object AiBufferUtils {
    fun allocateDirectFloatBuffer(capacity: Int): FloatBuffer {
        return ByteBuffer.allocateDirect(capacity * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    }
}
