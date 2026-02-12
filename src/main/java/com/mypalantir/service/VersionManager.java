package com.mypalantir.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 版本号管理工具类
 * 支持语义化版本规范（Semantic Versioning）
 */
public class VersionManager {
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-(.+))?$");

    /**
     * 解析版本号
     */
    public static Version parseVersion(String versionStr) {
        if (versionStr == null || versionStr.trim().isEmpty()) {
            return new Version(1, 0, 0, null);
        }

        Matcher matcher = VERSION_PATTERN.matcher(versionStr.trim());
        if (matcher.matches()) {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            int patch = Integer.parseInt(matcher.group(3));
            String preRelease = matcher.group(4);
            return new Version(major, minor, patch, preRelease);
        }

        // 如果格式不正确，尝试解析为简单数字
        try {
            int major = Integer.parseInt(versionStr.trim());
            return new Version(major, 0, 0, null);
        } catch (NumberFormatException e) {
            return new Version(1, 0, 0, null);
        }
    }

    /**
     * 生成下一版本号
     */
    public static String generateNextVersion(String currentVersion, VersionType type) {
        Version version = parseVersion(currentVersion);
        
        switch (type) {
            case MAJOR:
                return new Version(version.major + 1, 0, 0, null).toString();
            case MINOR:
                return new Version(version.major, version.minor + 1, 0, null).toString();
            case PATCH:
                return new Version(version.major, version.minor, version.patch + 1, null).toString();
            default:
                return new Version(version.major, version.minor, version.patch + 1, null).toString();
        }
    }

    /**
     * 检查版本兼容性
     * 主版本号相同则兼容
     */
    public static boolean isCompatible(String v1, String v2) {
        Version version1 = parseVersion(v1);
        Version version2 = parseVersion(v2);
        return version1.major == version2.major;
    }

    /**
     * 版本号类
     */
    public static class Version {
        public final int major;
        public final int minor;
        public final int patch;
        public final String preRelease;

        public Version(int major, int minor, int patch, String preRelease) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.preRelease = preRelease;
        }

        @Override
        public String toString() {
            String version = major + "." + minor + "." + patch;
            if (preRelease != null && !preRelease.isEmpty()) {
                version += "-" + preRelease;
            }
            return version;
        }
    }

    /**
     * 版本类型
     */
    public enum VersionType {
        MAJOR,  // 主版本号（不兼容的API修改）
        MINOR,  // 次版本号（向下兼容的功能性新增）
        PATCH   // 修订号（向下兼容的问题修正）
    }
}

