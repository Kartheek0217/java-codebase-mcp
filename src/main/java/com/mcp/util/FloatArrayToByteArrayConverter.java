package com.mcp.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Converter
public class FloatArrayToByteArrayConverter implements AttributeConverter<float[], byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(float[] attribute) {
        if (attribute == null)
            return null;
        ByteBuffer buffer = ByteBuffer.allocate(attribute.length * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (float f : attribute) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }

    @Override
    public float[] convertToEntityAttribute(byte[] dbData) {
        if (dbData == null)
            return null;
        ByteBuffer buffer = ByteBuffer.wrap(dbData);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        float[] floats = new float[dbData.length / 4];
        for (int i = 0; i < floats.length; i++) {
            floats[i] = buffer.getFloat();
        }
        return floats;
    }
}
