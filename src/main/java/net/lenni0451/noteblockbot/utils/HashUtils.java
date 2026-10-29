package net.lenni0451.noteblockbot.utils;

import lombok.SneakyThrows;

import java.security.MessageDigest;
import java.util.HexFormat;

public class HashUtils {

    @SneakyThrows
    public static String md5(final byte[] data) {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hash = md.digest(data);
        return HexFormat.of().formatHex(hash);
    }

}
