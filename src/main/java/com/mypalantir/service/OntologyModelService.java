package com.mypalantir.service;

import org.springframework.stereotype.Service;

import com.mypalantir.meta.Loader;
import com.mypalantir.meta.ObjectType;
import com.mypalantir.meta.OntologySchema;
import com.mypalantir.meta.Parser;
import com.mypalantir.meta.Validator;

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
    private final Loader loader;
    private String currentModelId;
    private String currentModelPath;
    
    public OntologyModelService(Loader loader) {
        this.loader = loader;
        // 初始化当前模型ID和路径
        this.currentModelPath = loader.getFilePath();
        // 从路径中提取模型ID
        if (this.currentModelPath != null) {
            File file = new File(this.currentModelPath);
            String fileName = file.getName();
            this.currentModelId = fileName.replace(".yaml", "");
        }
    }
    
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
     * 切换模型（热更新）
     * @param modelId 模型ID
     * @throws IOException 文件读取错误
     * @throws Validator.ValidationException Schema验证失败
     */
    public void switchModel(String modelId) throws IOException, Validator.ValidationException {
        String fileName = modelId.endsWith(".yaml") ? modelId : modelId + ".yaml";
        File file = new File(ontologyDir, fileName);
        if (!file.exists()) {
            throw new IOException("Model file not found: " + fileName);
        }
        
        // 使用Loader的switchModel方法进行热更新
        loader.switchModel(file.getAbsolutePath());
        
        // 更新当前模型ID和路径
        this.currentModelId = modelId;
        this.currentModelPath = file.getAbsolutePath();
    }
    
    /**
     * 获取当前模型ID
     * @return 当前模型ID
     */
    public String getCurrentModelId() {
        return currentModelId;
    }
    
    /**
     * 获取当前模型文件路径
     * @return 当前模型文件路径
     */
    public String getCurrentModelPath() {
        return currentModelPath;
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

