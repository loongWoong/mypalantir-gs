-- 画像模板表
CREATE TABLE IF NOT EXISTS PROFILE_TEMPLATES (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    display_name VARCHAR(100),
    description TEXT,
    entity_type VARCHAR(50) NOT NULL,
    craft_state TEXT NOT NULL,
    grid_layout TEXT,
    is_public BOOLEAN DEFAULT FALSE,
    creator_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_template_name UNIQUE (entity_type, name)
);

CREATE INDEX IF NOT EXISTS idx_template_entity ON PROFILE_TEMPLATES(entity_type);
CREATE INDEX IF NOT EXISTS idx_template_creator ON PROFILE_TEMPLATES(creator_id);
CREATE INDEX IF NOT EXISTS idx_template_public ON PROFILE_TEMPLATES(is_public);

-- 插入示例模板
INSERT INTO PROFILE_TEMPLATES (id, name, display_name, description, entity_type, craft_state, is_public, creator_id) VALUES 
('sample-gantry-1', 'default_gantry_profile', '默认门架画像', '门架基础指标展示模板', 'Gantry', '{}', true, 'system');
