package com.mypalantir.service;

import org.springframework.stereotype.Service;

import com.mypalantir.meta.ObjectType;
import com.mypalantir.meta.OntologySchema;
import com.mypalantir.meta.Parser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ontology 模型管理服务（简化方案：全局切换）
 */
@Service
public class OntologyModelService {
    private final String ontologyDir = "./ontology";
    
    /**
     * 列出所有可用的模型
     */
    public List<ModelInfo> listAvailableModels() {
        List<ModelInfo> models = new ArrayList<>();
        File dir = new File(ontologyDir);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".yaml"));
            if (files != null) {
                for (File file : files) {
                    String name = file.getName().replace(".yaml", "");
                    String displayName = getDisplayName(name);
                    models.add(new ModelInfo(name, file.getPath(), displayName));
                }
            }
        }
        return models;
    }
    
    /**
     * 获取指定模型的对象类型列表
     */
    public List<ObjectType> getObjectTypes(String modelId) throws IOException {
        String fileName = modelId.endsWith(".yaml") ? modelId : modelId + ".yaml";
        File file = new File(ontologyDir, fileName);
        if (!file.exists()) {
            throw new IOException("Model file not found: " + fileName);
        }
        
        Parser parser = new Parser(file.getPath());
        OntologySchema schema = parser.parse();
        return schema.getObjectTypes() != null ? schema.getObjectTypes() : Collections.emptyList();
    }
    
    /**
     * 获取模型的显示名称
     */
    private String getDisplayName(String modelId) {
        switch (modelId) {
            case "schema":
                return "收费系统";
            case "powergrid":
                return "电网规划";
            default:
                return modelId;
        }
    }
    
    /**
     * 模型信息
     */
    public static class ModelInfo {
        private String id;
        private String path;
        private String displayName;
        
        public ModelInfo(String id, String path, String displayName) {
            this.id = id;
            this.path = path;
            this.displayName = displayName;
        }
        
        public String getId() {
            return id;
        }
        
        public void setId(String id) {
            this.id = id;
        }
        
        public String getPath() {
            return path;
        }
        
        public void setPath(String path) {
            this.path = path;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }
}

