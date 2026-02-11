package com.mypalantir.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.mypalantir.meta.LinkType;
import com.mypalantir.meta.OntologySchema;
import com.mypalantir.meta.Validator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 本体可视化建模服务：负责 YML 生成和校验。
 */
@Service
public class OntologyBuilderService {
    private final ObjectMapper yamlMapper;

    public OntologyBuilderService() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    public ValidationResult validateAndGenerate(OntologySchema schema) {
        List<String> errors = new ArrayList<>();

        try {
            Validator validator = new Validator(schema);
            validator.validate();
        } catch (Validator.ValidationException e) {
            errors.add(e.getMessage());
        }

        errors.addAll(validateBusinessRules(schema));

        String yaml = "";
        try {
            yaml = yamlMapper.writeValueAsString(schema);
        } catch (JsonProcessingException e) {
            errors.add("YAML generation failed: " + e.getMessage());
        }

        return new ValidationResult(errors.isEmpty(), errors, yaml);
    }

    private List<String> validateBusinessRules(OntologySchema schema) {
        List<String> errors = new ArrayList<>();

        if (schema.getLinkTypes() == null) {
            return errors;
        }

        Set<String> relationUniqueCheck = new HashSet<>();
        for (LinkType linkType : schema.getLinkTypes()) {
            if (Objects.equals(linkType.getSourceType(), linkType.getTargetType())) {
                errors.add("link_type '" + linkType.getName() + "': self-loop is not allowed");
            }

            String uniqKey = String.join("::",
                safe(linkType.getName()),
                safe(linkType.getSourceType()),
                safe(linkType.getTargetType())
            );
            if (!relationUniqueCheck.add(uniqKey)) {
                errors.add("duplicate relation definition: " + uniqKey);
            }
        }

        return errors;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public static class ValidationResult {
        private boolean valid;
        private List<String> errors;
        private String yaml;

        public ValidationResult(boolean valid, List<String> errors, String yaml) {
            this.valid = valid;
            this.errors = errors;
            this.yaml = yaml;
        }

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public void setErrors(List<String> errors) {
            this.errors = errors;
        }

        public String getYaml() {
            return yaml;
        }

        public void setYaml(String yaml) {
            this.yaml = yaml;
        }
    }
}
