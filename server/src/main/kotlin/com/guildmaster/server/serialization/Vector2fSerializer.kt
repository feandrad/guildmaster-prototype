package com.guildmaster.server.serialization

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import org.joml.Vector2f

object Vector2fSerializer : KSerializer<Vector2f> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Vector2f") {
            element<Float>("x")
            element<Float>("y")
        }

    override fun serialize(encoder: Encoder, value: Vector2f) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeFloatElement(descriptor, 0, value.x)
        composite.encodeFloatElement(descriptor, 1, value.y)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): Vector2f {
        val dec = decoder.beginStructure(descriptor)
        var x = 0f
        var y = 0f
        loop@ while (true) {
            when (val index = dec.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> x = dec.decodeFloatElement(descriptor, 0)
                1 -> y = dec.decodeFloatElement(descriptor, 1)
                else -> throw SerializationException("Unknown index $index")
            }
        }
        dec.endStructure(descriptor)
        return Vector2f(x, y)
    }
}
