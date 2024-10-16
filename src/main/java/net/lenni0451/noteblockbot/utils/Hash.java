package net.lenni0451.noteblockbot.utils;

import lombok.SneakyThrows;

import java.security.MessageDigest;

public class Hash {

    @SneakyThrows
    public static String md5(final byte[] bytes) {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] array = md.digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : array) {
            sb.append(Integer.toHexString((b & 0xFF) | 0x100), 1, 3);
        }
        return sb.toString().toLowerCase();
    }

}
