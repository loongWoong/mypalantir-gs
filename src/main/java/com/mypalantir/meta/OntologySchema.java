package com.mypalantir.meta;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class OntologySchema {
    @JsonProperty("version")
    private String version;

    @JsonProperty("namespace")
    private String namespace;

    @JsonProperty("object_types")
    private List<ObjectType> objectTypes;

    @JsonProperty("link_types")
    private List<LinkType> linkTypes;

    @JsonIgnore
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @JsonIgnore
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    @JsonIgnore
    public List<ObjectType> getObjectTypes() {
        return objectTypes;
    }

    public void setObjectTypes(List<ObjectType> objectTypes) {
        this.objectTypes = objectTypes;
    }

    @JsonIgnore
    public List<LinkType> getLinkTypes() {
        return linkTypes;
    }

    public void setLinkTypes(List<LinkType> linkTypes) {
        this.linkTypes = linkTypes;
    }
}
