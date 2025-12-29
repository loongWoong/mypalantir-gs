package com.mypalantir.repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

public class PathManager {
    private final String dataRoot;
    private final String namespace;
    private static final Pattern NON_ASCII_PATTERN = Pattern.compile("[^\\x00-\\x7F]");

    public PathManager(String dataRoot, String namespace) {
        this.dataRoot = dataRoot;
        this.namespace = normalizeNamespace(namespace);
    }

    public String getInstancePath(String objectType, String id) {
        String normalizedType = normalizeName(objectType);
        return String.format("%s/%s/%s/%s.json", dataRoot, this.namespace, normalizedType, id);
    }

    public String getInstanceDir(String objectType) {
        String normalizedType = normalizeName(objectType);
        return String.format("%s/%s/%s", dataRoot, this.namespace, normalizedType);
    }

    public String getLinkPath(String linkType, String id) {
        String normalizedType = normalizeName(linkType);
        return String.format("%s/%s/links/%s/%s.json", dataRoot, this.namespace, normalizedType, id);
    }

    public String getLinkDir(String linkType) {
        String normalizedType = normalizeName(linkType);
        return String.format("%s/%s/links/%s", dataRoot, this.namespace, normalizedType);
    }

    private String normalizeNamespace(String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return "default";
        }
        return namespace.toLowerCase()
            .replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private String normalizeName(String name) {
        if (name == null || name.isEmpty()) {
            return "unknown";
        }

        // 检查是否包含非ASCII字符
        boolean hasNonASCII = NON_ASCII_PATTERN.matcher(name).find();

        if (hasNonASCII) {
            // 对于包含中文等非ASCII字符的名称，使用MD5哈希
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] hash = md.digest(name.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : hash) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("MD5 algorithm not available", e);
            }
        }

        // 对于纯ASCII名称，转换为小写，特殊字符替换为下划线
        return name.toLowerCase().replaceAll("[^a-zA-Z0-9_]", "_");
    }
}

