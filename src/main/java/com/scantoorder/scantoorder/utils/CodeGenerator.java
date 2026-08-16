package com.scantoorder.scantoorder.utils;

import com.scantoorder.scantoorder.data.model.CodePrefix;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.UUID;


public class CodeGenerator {
    public static String generate(CodePrefix codePrefix) {
        return codePrefix.name()+"-"+ UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
