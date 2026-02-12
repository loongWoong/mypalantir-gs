package com.mypalantir.meta;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 本体模型版本信息
 */
public class OntologyVersion {
    @JsonProperty("version")
    private String version;

    @JsonProperty("namespace")
    private String namespace;

    @JsonProperty("filename")
    private String filename;

    @JsonProperty("file_path")
    private String filePath;

    @JsonProperty("previous_version")
    private String previousVersion;

    @JsonProperty("commit_message")
    private String commitMessage;

    @JsonProperty("author")
    private String author;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("workspace_id")
    private String workspaceId;

    @JsonProperty("workspace_name")
    private String workspaceName;

    @JsonProperty("changes")
    private List<String> changes;

    public OntologyVersion() {
    }

    public OntologyVersion(String version, String namespace, String filename, String filePath) {
        this.version = version;
        this.namespace = namespace;
        this.filename = filename;
        this.filePath = filePath;
        this.timestamp = System.currentTimeMillis();
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getPreviousVersion() {
        return previousVersion;
    }

    public void setPreviousVersion(String previousVersion) {
        this.previousVersion = previousVersion;
    }

    public String getCommitMessage() {
        return commitMessage;
    }

    public void setCommitMessage(String commitMessage) {
        this.commitMessage = commitMessage;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getWorkspaceName() {
        return workspaceName;
    }

    public void setWorkspaceName(String workspaceName) {
        this.workspaceName = workspaceName;
    }

    public List<String> getChanges() {
        return changes;
    }

    public void setChanges(List<String> changes) {
        this.changes = changes;
    }
}

