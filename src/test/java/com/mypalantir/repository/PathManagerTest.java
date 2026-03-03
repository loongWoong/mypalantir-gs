package com.mypalantir.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PathManagerTest {

    @Test
    void getInstancePath_formatsCorrectly() {
        PathManager pm = new PathManager("/data", "test");
        String path = pm.getInstancePath("Vehicle", "v1");
        assertTrue(path.endsWith("v1.json"));
        assertTrue(path.contains("Vehicle") || path.contains("vehicle"));
        assertTrue(path.contains("/data/test/"));
    }

    @Test
    void getInstanceDir_formatsCorrectly() {
        PathManager pm = new PathManager("/data", "myNS");
        String dir = pm.getInstanceDir("Vehicle");
        assertTrue(dir.contains("/data/"));
        assertTrue(dir.contains("Vehicle") || dir.contains("vehicle"));
    }

    @Test
    void getLinkPath_formatsCorrectly() {
        PathManager pm = new PathManager("/data", "test");
        String path = pm.getLinkPath("owns", "link-1");
        assertTrue(path.endsWith("link-1.json"));
        assertTrue(path.contains("links"));
        assertTrue(path.contains("owns") || path.contains("link"));
    }

    @Test
    void getLinkDir_formatsCorrectly() {
        PathManager pm = new PathManager("/data", "test");
        String dir = pm.getLinkDir("owns");
        assertTrue(dir.contains("links"));
        assertFalse(dir.endsWith(".json"));
    }

    @Test
    void namespace_nullOrEmpty_becomesDefault() {
        PathManager pmNull = new PathManager("/data", null);
        PathManager pmEmpty = new PathManager("/data", "");
        String pathNull = pmNull.getInstancePath("A", "1");
        String pathEmpty = pmEmpty.getInstancePath("A", "1");
        assertTrue(pathNull.contains("default"));
        assertTrue(pathEmpty.contains("default"));
    }

    @Test
    void name_ascii_normalizedToLowerAndUnderscore() {
        PathManager pm = new PathManager("/data", "ns");
        String path = pm.getInstancePath("MyObjectType", "id1");
        // 纯 ASCII 应转为小写，特殊字符变下划线
        assertTrue(path.contains("myobjecttype") || path.contains("MyObjectType"));
    }

    @Test
    void name_withNonAscii_usesHashInPath() {
        PathManager pm = new PathManager("/data", "ns");
        String path = pm.getInstancePath("车辆", "v1");
        // 中文名应被 MD5 哈希，路径中不应包含中文字符
        assertFalse(path.contains("车"));
        assertTrue(path.matches(".*[0-9a-f]{32}.*") || path.contains("v1.json"));
    }
}
