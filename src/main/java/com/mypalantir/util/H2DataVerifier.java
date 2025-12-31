package com.mypalantir.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 验证 H2 数据库中的数据
 */
public class H2DataVerifier {
    public static void main(String[] args) {
        String jdbcUrl = "jdbc:h2:file:./data/h2/mypalantir";
        String username = "sa";
        String password = "";

        try {
            Class.forName("org.h2.Driver");
            try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
                 Statement stmt = conn.createStatement()) {

                System.out.println("=== 车辆与通行介质关联关系（一对多）===\n");
                
                String sql = "SELECT " +
                    "v.vehicle_id, " +
                    "v.plate_number, " +
                    "v.vehicle_type, " +
                    "v.owner_name, " +
                    "m.media_number, " +
                    "m.media_type, " +
                    "m.issue_date, " +
                    "vm.bind_time, " +
                    "vm.bind_status " +
                    "FROM vehicles v " +
                    "LEFT JOIN vehicle_media vm ON v.vehicle_id = vm.vehicle_id " +
                    "LEFT JOIN media m ON vm.media_id = m.media_id " +
                    "ORDER BY v.plate_number, m.media_number";
                
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    System.out.printf("%-10s %-12s %-12s %-10s %-15s %-10s %-12s %-20s %-10s%n", 
                        "车辆ID", "车牌号", "车辆类型", "车主", "介质编号", "介质类型", "发行日期", "绑定时间", "绑定状态");
                    System.out.println("------------------------------------------------------------------------------------------------------------------------");
                    
                    while (rs.next()) {
                        System.out.printf("%-10s %-12s %-12s %-10s %-15s %-10s %-12s %-20s %-10s%n",
                            rs.getString("vehicle_id"),
                            rs.getString("plate_number"),
                            rs.getString("vehicle_type"),
                            rs.getString("owner_name"),
                            rs.getString("media_number"),
                            rs.getString("media_type"),
                            rs.getString("issue_date"),
                            rs.getString("bind_time"),
                            rs.getString("bind_status"));
                    }
                }
                
                System.out.println("\n=== 统计信息 ===");
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM vehicles")) {
                    if (rs.next()) {
                        System.out.println("车辆总数: " + rs.getInt("cnt"));
                    }
                }
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM media")) {
                    if (rs.next()) {
                        System.out.println("通行介质总数: " + rs.getInt("cnt"));
                    }
                }
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM vehicle_media")) {
                    if (rs.next()) {
                        System.out.println("车辆-通行介质关联总数: " + rs.getInt("cnt"));
                    }
                }
                
                // 验证一对多关系（一个车辆可以持有多个通行介质）
                System.out.println("\n=== 一对多关系验证 ===");
                try (ResultSet rs = stmt.executeQuery(
                    "SELECT vehicle_id, COUNT(*) AS cnt FROM vehicle_media GROUP BY vehicle_id ORDER BY cnt DESC")) {
                    boolean hasMultiple = false;
                    System.out.println("每个车辆持有的通行介质数量：");
                    while (rs.next()) {
                        int count = rs.getInt("cnt");
                        if (count > 1) {
                            hasMultiple = true;
                        }
                        System.out.println("  车辆 " + rs.getString("vehicle_id") + ": " + count + " 个通行介质");
                    }
                    if (hasMultiple) {
                        System.out.println("✓ 一对多关系验证通过：存在一个车辆持有多个通行介质的情况");
                    } else {
                        System.out.println("ℹ 当前数据中每个车辆只持有一个通行介质（但结构支持一对多）");
                    }
                }
            }
        } catch (ClassNotFoundException | SQLException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

