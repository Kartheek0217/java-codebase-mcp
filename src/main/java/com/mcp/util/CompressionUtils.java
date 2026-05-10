package com.mcp.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class CompressionUtils {

    public static byte[] compress(String str) throws IOException {
        if (str == null || str.isEmpty()) {
            return new byte[0];
        }
        ByteArrayOutputStream obj = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(obj);
        gzip.write(str.getBytes(StandardCharsets.UTF_8));
        gzip.flush();
        gzip.close();
        return obj.toByteArray();
    }

    public static String decompress(byte[] compressed) throws IOException {
        if (compressed == null || compressed.length == 0) {
            return "";
        }
        GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = gis.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        return bos.toString(StandardCharsets.UTF_8);
    }
}
