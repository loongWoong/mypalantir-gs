package com.mypalantir.controller;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    void success_returns200AndData() {
        ApiResponse<String> res = ApiResponse.success("ok");
        assertEquals(200, res.getCode());
        assertEquals("success", res.getMessage());
        assertEquals("ok", res.getData());
        assertNotNull(res.getTimestamp());
    }

    @Test
    void error_returnsCodeAndMessage() {
        ApiResponse<Void> res = ApiResponse.error(404, "Not Found");
        assertEquals(404, res.getCode());
        assertEquals("Not Found", res.getMessage());
        assertNull(res.getData());
    }

    @Test
    void errorItem_holdsFieldAndMessage() {
        ApiResponse.ErrorItem item = new ApiResponse.ErrorItem("field1", "invalid");
        assertEquals("field1", item.getField());
        assertEquals("invalid", item.getMessage());
        item.setField("f2");
        item.setMessage("msg");
        assertEquals("f2", item.getField());
        assertEquals("msg", item.getMessage());
    }

    @Test
    void setDataAndGetters() {
        ApiResponse<List<String>> res = ApiResponse.success(List.of("a"));
        res.setCode(201);
        res.setMessage("created");
        res.setData(List.of("a", "b"));
        res.setTimestamp("2024-01-01T00:00:00Z");
        assertEquals(201, res.getCode());
        assertEquals("created", res.getMessage());
        assertEquals(2, res.getData().size());
        assertEquals("2024-01-01T00:00:00Z", res.getTimestamp());
    }
}
