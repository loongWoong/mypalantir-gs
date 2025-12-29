package com.mypalantir.meta;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;

public class Parser {
    private final String filePath;
    private final ObjectMapper yamlMapper;

    public Parser(String filePath) {
        this.filePath = filePath;
        // YAML 解析器：使用小写字段名（匹配 YAML 文件）
        // 使用字段上的 @JsonProperty 注解进行反序列化
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public OntologySchema parse() throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("DSL file not found: " + filePath);
        }
        return yamlMapper.readValue(file, OntologySchema.class);
    }
}

