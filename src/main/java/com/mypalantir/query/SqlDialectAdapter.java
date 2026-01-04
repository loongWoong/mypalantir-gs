package com.mypalantir.query;

import java.util.regex.Pattern;

/**
 * SQL 方言适配器
 * 处理不同数据库的 SQL 语法差异
 */
public class SqlDialectAdapter {
    
    public enum DatabaseType {
        MYSQL,
        POSTGRESQL,
        ORACLE,
        SQLSERVER,
        H2,
        UNKNOWN
    }
    
    /**
     * 根据数据库类型字符串获取 DatabaseType
     */
    public static DatabaseType getDatabaseType(String dbType) {
        if (dbType == null || dbType.isEmpty()) {
            return DatabaseType.MYSQL; // 默认 MySQL
        }
        
        String lowerType = dbType.toLowerCase();
        switch (lowerType) {
            case "mysql":
                return DatabaseType.MYSQL;
            case "postgresql":
            case "postgres":
                return DatabaseType.POSTGRESQL;
            case "oracle":
                return DatabaseType.ORACLE;
            case "sqlserver":
            case "mssql":
                return DatabaseType.SQLSERVER;
            case "h2":
                return DatabaseType.H2;
            default:
                // 根据 JDBC URL 判断
                if (lowerType.contains("mysql")) {
                    return DatabaseType.MYSQL;
                } else if (lowerType.contains("postgres")) {
                    return DatabaseType.POSTGRESQL;
                } else if (lowerType.contains("oracle")) {
                    return DatabaseType.ORACLE;
                } else if (lowerType.contains("sqlserver") || lowerType.contains("mssql")) {
                    return DatabaseType.SQLSERVER;
                } else if (lowerType.contains("h2")) {
                    return DatabaseType.H2;
                }
                return DatabaseType.UNKNOWN;
        }
    }
    
    /**
     * 根据 JDBC URL 获取数据库类型
     */
    public static DatabaseType getDatabaseTypeFromJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            return DatabaseType.MYSQL; // 默认 MySQL
        }
        
        String lowerUrl = jdbcUrl.toLowerCase();
        if (lowerUrl.contains("mysql")) {
            return DatabaseType.MYSQL;
        } else if (lowerUrl.contains("postgresql") || lowerUrl.contains("postgres")) {
            return DatabaseType.POSTGRESQL;
        } else if (lowerUrl.contains("oracle")) {
            return DatabaseType.ORACLE;
        } else if (lowerUrl.contains("sqlserver") || lowerUrl.contains("mssql")) {
            return DatabaseType.SQLSERVER;
        } else if (lowerUrl.contains("h2")) {
            return DatabaseType.H2;
        }
        
        return DatabaseType.MYSQL; // 默认 MySQL
    }
    
    /**
     * 转换 SQL 以适配目标数据库
     * @param sql 原始 SQL
     * @param targetType 目标数据库类型
     * @return 转换后的 SQL
     */
    public static String adaptSql(String sql, DatabaseType targetType) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }
        
        switch (targetType) {
            case MYSQL:
                return adaptToMySQL(sql);
            case POSTGRESQL:
                return adaptToPostgreSQL(sql);
            case ORACLE:
                return adaptToOracle(sql);
            case SQLSERVER:
                return adaptToSQLServer(sql);
            case H2:
                return adaptToH2(sql);
            default:
                // 默认使用 MySQL 语法
                return adaptToMySQL(sql);
        }
    }
    
    /**
     * 转换为 MySQL 语法
     */
    private static String adaptToMySQL(String sql) {
        String result = sql;
        
        // 1. 转换 FETCH NEXT ... ROWS ONLY 为 LIMIT
        // 匹配: FETCH NEXT n ROWS ONLY 或 FETCH FIRST n ROWS ONLY
        Pattern fetchPattern = Pattern.compile(
            "(?i)FETCH\\s+(?:NEXT|FIRST)\\s+(\\d+)\\s+ROWS?\\s+ONLY",
            Pattern.CASE_INSENSITIVE
        );
        result = fetchPattern.matcher(result).replaceAll("LIMIT $1");
        
        // 2. 转换 OFFSET ... FETCH NEXT ... 为 LIMIT ... OFFSET ...
        // 匹配: OFFSET n ROWS FETCH NEXT m ROWS ONLY
        Pattern offsetFetchPattern = Pattern.compile(
            "(?i)OFFSET\\s+(\\d+)\\s+ROWS?\\s+FETCH\\s+(?:NEXT|FIRST)\\s+(\\d+)\\s+ROWS?\\s+ONLY",
            Pattern.CASE_INSENSITIVE
        );
        result = offsetFetchPattern.matcher(result).replaceAll("LIMIT $2 OFFSET $1");
        
        // 3. 确保双引号已转换为反引号（如果还没有）
        // 这个应该在 replaceColumnNames 中已经处理了
        
        return result;
    }
    
    /**
     * 转换为 PostgreSQL 语法
     */
    private static String adaptToPostgreSQL(String sql) {
        String result = sql;
        
        // PostgreSQL 支持 LIMIT 和 OFFSET，但也可以使用 FETCH
        // 为了统一，将 FETCH 转换为 LIMIT
        Pattern fetchPattern = Pattern.compile(
            "(?i)FETCH\\s+(?:NEXT|FIRST)\\s+(\\d+)\\s+ROWS?\\s+ONLY",
            Pattern.CASE_INSENSITIVE
        );
        result = fetchPattern.matcher(result).replaceAll("LIMIT $1");
        
        Pattern offsetFetchPattern = Pattern.compile(
            "(?i)OFFSET\\s+(\\d+)\\s+ROWS?\\s+FETCH\\s+(?:NEXT|FIRST)\\s+(\\d+)\\s+ROWS?\\s+ONLY",
            Pattern.CASE_INSENSITIVE
        );
        result = offsetFetchPattern.matcher(result).replaceAll("LIMIT $2 OFFSET $1");
        
        return result;
    }
    
    /**
     * 转换为 Oracle 语法
     */
    private static String adaptToOracle(String sql) {
        // Oracle 12c+ 支持 FETCH，但旧版本需要使用 ROWNUM
        // 这里保持 FETCH 语法（Oracle 12c+）
        return sql;
    }
    
    /**
     * 转换为 SQL Server 语法
     */
    private static String adaptToSQLServer(String sql) {
        // SQL Server 2012+ 支持 FETCH，保持原样
        return sql;
    }
    
    /**
     * 转换为 H2 语法
     */
    private static String adaptToH2(String sql) {
        // H2 支持 LIMIT，将 FETCH 转换为 LIMIT
        String result = sql;
        
        Pattern fetchPattern = Pattern.compile(
            "(?i)FETCH\\s+(?:NEXT|FIRST)\\s+(\\d+)\\s+ROWS?\\s+ONLY",
            Pattern.CASE_INSENSITIVE
        );
        result = fetchPattern.matcher(result).replaceAll("LIMIT $1");
        
        Pattern offsetFetchPattern = Pattern.compile(
            "(?i)OFFSET\\s+(\\d+)\\s+ROWS?\\s+FETCH\\s+(?:NEXT|FIRST)\\s+(\\d+)\\s+ROWS?\\s+ONLY",
            Pattern.CASE_INSENSITIVE
        );
        result = offsetFetchPattern.matcher(result).replaceAll("LIMIT $2 OFFSET $1");
        
        return result;
    }
}
