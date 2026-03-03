package com.mypalantir.service;

import com.mypalantir.service.VersionManager.Version;
import com.mypalantir.service.VersionManager.VersionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VersionManager 解析版本号、生成下一版本、兼容性判断及 Version 的单元测试。
 */
class VersionManagerTest {

    @Test
    void parseVersion_null_returnsDefault() {
        Version v = VersionManager.parseVersion(null);
        assertEquals(1, v.major);
        assertEquals(0, v.minor);
        assertEquals(0, v.patch);
        assertNull(v.preRelease);
    }

    @Test
    void parseVersion_emptyString_returnsDefault() {
        Version v = VersionManager.parseVersion("   ");
        assertEquals(1, v.major);
        assertEquals(0, v.minor);
        assertEquals(0, v.patch);
    }

    @Test
    void parseVersion_semver() {
        Version v = VersionManager.parseVersion("2.3.1");
        assertEquals(2, v.major);
        assertEquals(3, v.minor);
        assertEquals(1, v.patch);
        assertNull(v.preRelease);
    }

    @Test
    void parseVersion_semverWithPreRelease() {
        Version v = VersionManager.parseVersion("1.0.0-beta.1");
        assertEquals(1, v.major);
        assertEquals(0, v.minor);
        assertEquals(0, v.patch);
        assertEquals("beta.1", v.preRelease);
    }

    @Test
    void parseVersion_simpleNumber() {
        Version v = VersionManager.parseVersion("5");
        assertEquals(5, v.major);
        assertEquals(0, v.minor);
        assertEquals(0, v.patch);
    }

    @Test
    void parseVersion_invalid_returnsDefault() {
        Version v = VersionManager.parseVersion("abc");
        assertEquals(1, v.major);
        assertEquals(0, v.minor);
        assertEquals(0, v.patch);
    }

    @Test
    void generateNextVersion_major() {
        String next = VersionManager.generateNextVersion("1.2.3", VersionType.MAJOR);
        assertEquals("2.0.0", next);
    }

    @Test
    void generateNextVersion_minor() {
        String next = VersionManager.generateNextVersion("1.2.3", VersionType.MINOR);
        assertEquals("1.3.0", next);
    }

    @Test
    void generateNextVersion_patch() {
        String next = VersionManager.generateNextVersion("1.2.3", VersionType.PATCH);
        assertEquals("1.2.4", next);
    }

    @Test
    void isCompatible_sameMajor_returnsTrue() {
        assertTrue(VersionManager.isCompatible("1.0.0", "1.2.3"));
        assertTrue(VersionManager.isCompatible("2.5.0", "2.0.0"));
    }

    @Test
    void isCompatible_differentMajor_returnsFalse() {
        assertFalse(VersionManager.isCompatible("1.0.0", "2.0.0"));
        assertFalse(VersionManager.isCompatible("3.1.0", "2.1.0"));
    }

    @Test
    void version_toString_withoutPreRelease() {
        Version v = new Version(2, 1, 4, null);
        assertEquals("2.1.4", v.toString());
    }

    @Test
    void version_toString_withPreRelease() {
        Version v = new Version(1, 0, 0, "alpha");
        assertEquals("1.0.0-alpha", v.toString());
    }
}
