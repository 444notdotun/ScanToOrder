package com.scantoorder.scantoorder.utils;

import com.scantoorder.scantoorder.data.model.CodePrefix;

import java.security.SecureRandom;
import java.util.UUID;

public class CodeGenerator {
    private static final String JOIN_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int JOIN_CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate(CodePrefix codePrefix) {
        return codePrefix.name()+"-"+ UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public static String generateJoinCode() {
        StringBuilder code = new StringBuilder(JOIN_CODE_LENGTH);
        for (int i = 0; i < JOIN_CODE_LENGTH; i++) {
            code.append(JOIN_CODE_ALPHABET.charAt(RANDOM.nextInt(JOIN_CODE_ALPHABET.length())));
        }
        return code.toString();
    }
}
