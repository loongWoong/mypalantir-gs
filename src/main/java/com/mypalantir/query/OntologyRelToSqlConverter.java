package com.mypalantir.query;

import org.apache.calcite.rel.rel2sql.RelToSqlConverter;
import org.apache.calcite.sql.SqlDialect;
import org.apache.calcite.sql.SqlNode;

/**
 * 自定义的 RelToSqlConverter
 * 将 RelNode 转换为 SQL
 *
 * 设计理念：
 * - RelNode 使用 Ontology 概念（逻辑层）
 * - JdbcOntologyTable.scan() 负责 Ontology→DB 列名映射（物理层）
 * - JOIN 条件由 RelNodeBuilder 正确构建，无需 SQL 层修补
 */
public class OntologyRelToSqlConverter extends RelToSqlConverter {
    private final SqlDialect dialect;

    public OntologyRelToSqlConverter(SqlDialect dialect) {
        super(dialect);
        this.dialect = dialect;
    }

    /**
     * 获取生成的 SQL
     */
    public String getMappedSql(Result result) {
        return getMappedSql(result, null);
    }

    /**
     * 获取生成的 SQL（兼容旧签名）
     */
    public String getMappedSql(Result result, OntologyQuery query) {
        SqlNode sqlNode = result.asStatement();
        return sqlNode.toSqlString(dialect).getSql();
    }
}
