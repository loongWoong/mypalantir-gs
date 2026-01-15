# 画像功能设计方案

## 一、设计背景与定位

### 1.1 核心理念

画像功能是基于现有**指标体系**的高级应用层，本质上是**多维度指标聚合结果的可视化报表**。在 MyPalantir 的 Ontology 驱动架构下，画像不是独立的数据层，而是指标计算结果的组织和展示形式。

```
【数据分层架构】
┌─────────────────────────────────────────┐
│  画像层 (Profile Layer)                  │  ← 报表展示层
│  - 门架画像、车辆画像                     │
│  - 基于指标组合 + 可视化                  │
└─────────────────────────────────────────┘
              ↑ 调用
┌─────────────────────────────────────────┐
│  指标层 (Metric Layer)                   │  ← 已有能力
│  - 原子指标、派生指标、复合指标            │
│  - 聚合计算、时间粒度、维度分析            │
└─────────────────────────────────────────┘
              ↑ 查询
┌─────────────────────────────────────────┐
│  事实层 (Fact Layer)                     │  ← 数据源
│  - EntryTransaction 入口交易              │
│  - ExitTransaction 出口交易               │
│  - GantryTransaction 门架交易             │
└─────────────────────────────────────────┘
```

### 1.2 业务价值

**门架画像**：
- 识别高价值门架（收入贡献度）
- 分析流量分布和繁忙时段
- 优化资源配置和运营策略
- 异常检测（流量突降、故障预警）

**车辆画像**：
- 用户分层（高频/低频用户）
- 行为分析（常用路线、出行习惯）
- 精准营销和定价策略
- 风险识别（异常通行行为）

---

## 二、技术架构设计

### 2.1 整体架构

```
┌──────────────────────────────────────────────────────────┐
│                   前端展示层                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │ 门架画像页面  │  │ 车辆画像页面  │  │ 画像对比页面  │   │
│  └──────────────┘  └──────────────┘  └──────────────┘   │
└──────────────────────────────────────────────────────────┘
                         ↓ HTTP API
┌──────────────────────────────────────────────────────────┐
│                   后端服务层                               │
│  ┌─────────────────────────────────────────────────┐     │
│  │  ProfileService (画像服务)                       │     │
│  │  - generateProfile()    生成画像                 │     │
│  │  - getProfileMetrics()  获取指标组合             │     │
│  │  - compareProfiles()    画像对比                 │     │
│  └─────────────────────────────────────────────────┘     │
│                         ↓ 调用                            │
│  ┌─────────────────────────────────────────────────┐     │
│  │  MetricService (指标服务) - 已有                 │     │
│  │  - calculateAtomicMetric()   计算原子指标        │     │
│  │  - calculateMetric()         计算派生/复合指标   │     │
│  └─────────────────────────────────────────────────┘     │
│                         ↓ 调用                            │
│  ┌─────────────────────────────────────────────────┐     │
│  │  QueryService (查询服务) - 已有                  │     │
│  │  - executeQuery()            执行 OntologyQuery │     │
│  └─────────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────────┘
                         ↓ SQL
┌──────────────────────────────────────────────────────────┐
│                   数据存储层                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │ 交易事实表    │  │ 指标定义存储  │  │ 画像缓存表    │   │
│  │ (已有)       │  │ (已有)       │  │ (可选)       │   │
│  └──────────────┘  └──────────────┘  └──────────────┘   │
└──────────────────────────────────────────────────────────┘
```

### 2.2 核心设计原则

1. **复用优先**：充分利用现有指标计算能力，避免重复开发
2. **轻量实现**：画像不引入新的数据模型，仅作为指标组合的展示形式
3. **灵活配置**：画像维度和指标组合可配置，支持业务迭代
4. **按需缓存**：初期实时计算，性能瓶颈时再引入缓存机制

---

## 三、实现方案

### 3.1 方案选择

#### **方案A：轻量级实现（推荐首选）**

**设计思路**：
- 画像 = 前端发起多个指标查询 → 并发请求 → 组装结果 → 可视化展示
- 不新增数据表，不修改 Schema，纯应用层实现

**优势**：
- ✅ 实现快速，无需数据建模
- ✅ 数据实时，无延迟
- ✅ 架构简洁，易于维护

**适用场景**：
- 画像访问频率不高（< 100 QPS）
- 底层数据量可控（单次查询 < 1s）
- 快速验证业务价值

---

#### **方案B：预计算缓存（性能优化）**

**设计思路**：
- 后端定时任务批量计算画像 → 存储到缓存表 → 前端读取缓存

**优势**：
- ✅ 查询性能高（直接读缓存）
- ✅ 支持历史画像对比
- ✅ 降低数据库压力

**新增数据表**：
```sql
-- 通用画像缓存表
CREATE TABLE PROFILE_CACHE (
    id VARCHAR(36) PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,     -- Gantry/Vehicle/TollStation
    entity_id VARCHAR(50) NOT NULL,       -- 实体ID
    profile_type VARCHAR(50) NOT NULL,    -- daily/weekly/monthly
    profile_date DATE NOT NULL,           -- 画像日期
    metrics_json TEXT NOT NULL,           -- JSON格式存储所有指标
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(entity_type, entity_id, profile_type, profile_date)
);

CREATE INDEX idx_profile_entity ON PROFILE_CACHE(entity_type, entity_id, profile_date);
```

**适用场景**：
- 画像访问频率高
- 需要历史画像趋势分析
- 底层数据量大，实时计算慢

---

### 3.2 方案A详细设计（轻量级实现）

#### **3.2.1 后端接口设计**

##### **API 接口定义**

```java
// src/main/java/com/mypalantir/controller/ProfileController.java
package com.mypalantir.controller;

@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileController {
    
    private final ProfileService profileService;
    
    /**
     * 获取门架画像
     * @param gantryId 门架ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 画像数据
     */
    @GetMapping("/gantry/{gantryId}")
    public ApiResponse<GantryProfileData> getGantryProfile(
        @PathVariable String gantryId,
        @RequestParam(defaultValue = "2024-01-01") String startDate,
        @RequestParam(defaultValue = "2024-12-31") String endDate
    ) {
        GantryProfileData profile = profileService.generateGantryProfile(
            gantryId, 
            LocalDate.parse(startDate), 
            LocalDate.parse(endDate)
        );
        return ApiResponse.success(profile);
    }
    
    /**
     * 获取车辆画像
     */
    @GetMapping("/vehicle/{vehicleId}")
    public ApiResponse<VehicleProfileData> getVehicleProfile(
        @PathVariable String vehicleId,
        @RequestParam(defaultValue = "2024-01-01") String startDate,
        @RequestParam(defaultValue = "2024-12-31") String endDate
    ) {
        VehicleProfileData profile = profileService.generateVehicleProfile(
            vehicleId,
            LocalDate.parse(startDate),
            LocalDate.parse(endDate)
        );
        return ApiResponse.success(profile);
    }
    
    /**
     * 批量获取门架画像（用于对比）
     */
    @PostMapping("/gantry/batch")
    public ApiResponse<List<GantryProfileData>> batchGetGantryProfiles(
        @RequestBody BatchProfileRequest request
    ) {
        List<GantryProfileData> profiles = profileService.batchGenerateGantryProfiles(
            request.getGantryIds(),
            LocalDate.parse(request.getStartDate()),
            LocalDate.parse(request.getEndDate())
        );
        return ApiResponse.success(profiles);
    }
}
```

##### **数据传输对象 (DTO)**

```java
// src/main/java/com/mypalantir/dto/GantryProfileData.java
package com.mypalantir.dto;

@Data
public class GantryProfileData {
    // 基础信息
    private String gantryId;
    private String gantryHex;
    private String gantryName;
    
    // 时间范围
    private String startDate;
    private String endDate;
    private Integer totalDays;
    
    // 核心指标
    private Long totalTransactionCount;      // 总交易量
    private Double totalRevenue;             // 总收入(元)
    private Double avgDailyRevenue;          // 日均收入
    private Double avgTransactionAmount;     // 平均单笔金额
    
    // 时间分布
    private Map<String, Integer> hourlyDistribution;  // 小时分布
    private String peakHour;                          // 高峰时段
    private String peakHourCount;                     // 高峰时段交易量
    
    // 车型分布
    private Map<String, VehicleTypeStats> vehicleTypeDistribution;
    
    // 繁忙度评级
    private String busyLevel;  // LOW/MEDIUM/HIGH
    private Double busyScore;  // 0-100 分值
    
    // 趋势数据（可选）
    private List<DailyTrendData> dailyTrends;
}

@Data
class VehicleTypeStats {
    private String vehicleType;
    private Integer count;
    private Double percentage;
    private Double totalFee;
}

@Data
class DailyTrendData {
    private String date;
    private Integer transactionCount;
    private Double revenue;
}
```

```java
// src/main/java/com/mypalantir/dto/VehicleProfileData.java
package com.mypalantir.dto;

@Data
public class VehicleProfileData {
    // 基础信息
    private String vehicleId;
    private String plateNum;
    private String plateColor;
    private Integer vehicleType;
    
    // 时间范围
    private String startDate;
    private String endDate;
    
    // 通行统计
    private Integer totalTrips;              // 总通行次数
    private Long totalDistance;              // 累计里程(米)
    private Double totalFee;                 // 累计费用(元)
    private Double avgTripDistance;          // 平均单次里程
    private Double avgTripFee;               // 平均单次费用
    
    // 常用路线
    private List<FrequentRoute> frequentRoutes;
    
    // 常用收费站
    private List<FrequentStation> frequentStations;
    
    // 活跃时段
    private Map<String, Integer> activityPattern;  // 小时 -> 次数
    private String mostActiveHour;
    
    // 用户分类
    private String userType;  // HIGH_FREQ/MEDIUM_FREQ/LOW_FREQ
    private Double activityScore;  // 活跃度分值
    
    // 趋势数据
    private List<MonthlyTrendData> monthlyTrends;
}

@Data
class FrequentRoute {
    private String entryStation;
    private String exitStation;
    private Integer tripCount;
    private Double percentage;
}

@Data
class FrequentStation {
    private String stationId;
    private String stationName;
    private Integer usageCount;
    private Double percentage;
}
```

---

#### **3.2.2 业务逻辑实现**

```java
// src/main/java/com/mypalantir/service/ProfileService.java
package com.mypalantir.service;

@Service
public class ProfileService {
    
    private final MetricCalculator metricCalculator;
    private final AtomicMetricService atomicMetricService;
    private final MetricService metricService;
    private final QueryService queryService;
    private final Loader loader;
    
    /**
     * 生成门架画像
     */
    public GantryProfileData generateGantryProfile(
        String gantryId, 
        LocalDate startDate, 
        LocalDate endDate
    ) throws Exception {
        GantryProfileData profile = new GantryProfileData();
        
        // 1. 获取门架基础信息
        Gantry gantry = getGantryInfo(gantryId);
        profile.setGantryId(gantryId);
        profile.setGantryHex(gantry.getGantryHex());
        profile.setStartDate(startDate.toString());
        profile.setEndDate(endDate.toString());
        
        // 2. 计算核心指标 - 使用现有指标系统
        // 2.1 总交易量
        MetricQuery countQuery = buildMetricQuery(startDate, endDate, gantryId);
        MetricResult countResult = calculateAtomicMetric("gantry_transaction_count", countQuery);
        profile.setTotalTransactionCount(extractValue(countResult, Long.class));
        
        // 2.2 总收入
        MetricResult revenueResult = calculateAtomicMetric("gantry_total_revenue", countQuery);
        profile.setTotalRevenue(extractValue(revenueResult, Double.class));
        
        // 2.3 平均金额
        MetricResult avgResult = calculateAtomicMetric("gantry_avg_amount", countQuery);
        profile.setAvgTransactionAmount(extractValue(avgResult, Double.class));
        
        // 3. 时间分布分析 - 使用 OntologyQuery 聚合
        Map<String, Integer> hourlyDist = calculateHourlyDistribution(gantryId, startDate, endDate);
        profile.setHourlyDistribution(hourlyDist);
        profile.setPeakHour(findPeakHour(hourlyDist));
        
        // 4. 车型分布分析
        Map<String, VehicleTypeStats> vehicleDist = calculateVehicleTypeDistribution(
            gantryId, startDate, endDate
        );
        profile.setVehicleTypeDistribution(vehicleDist);
        
        // 5. 繁忙度评级
        BusyLevel busyLevel = calculateBusyLevel(profile.getTotalTransactionCount(), endDate.toEpochDay() - startDate.toEpochDay());
        profile.setBusyLevel(busyLevel.getLevel());
        profile.setBusyScore(busyLevel.getScore());
        
        return profile;
    }
    
    /**
     * 计算小时分布
     */
    private Map<String, Integer> calculateHourlyDistribution(
        String gantryId, 
        LocalDate startDate, 
        LocalDate endDate
    ) throws Exception {
        // 构建 OntologyQuery - 按小时分组
        Map<String, Object> query = Map.of(
            "object", "GantryTransaction",
            "filter", List.of(
                List.of("=", "gantry_id", gantryId),
                List.of(">=", "trans_time", startDate.toString()),
                List.of("<=", "trans_time", endDate.toString())
            ),
            "select", List.of("trans_time"),
            "metrics", List.of(
                List.of("count", "*", "count")
            )
        );
        
        QueryExecutor.QueryResult result = queryService.executeQuery(query);
        
        // 处理结果，提取小时并聚合
        Map<String, Integer> hourlyMap = new HashMap<>();
        for (Map<String, Object> row : result.getRows()) {
            String timeStr = (String) row.get("trans_time");
            String hour = extractHour(timeStr);  // 提取小时部分
            Integer count = (Integer) row.get("count");
            hourlyMap.merge(hour, count, Integer::sum);
        }
        
        return hourlyMap;
    }
    
    /**
     * 计算车型分布
     */
    private Map<String, VehicleTypeStats> calculateVehicleTypeDistribution(
        String gantryId,
        LocalDate startDate,
        LocalDate endDate
    ) throws Exception {
        Map<String, Object> query = Map.of(
            "object", "GantryTransaction",
            "filter", List.of(
                List.of("=", "gantry_id", gantryId),
                List.of(">=", "trans_time", startDate.toString()),
                List.of("<=", "trans_time", endDate.toString())
            ),
            "groupBy", List.of("snapshot_vehicle_type"),
            "metrics", List.of(
                List.of("count", "*", "count"),
                List.of("sum", "fee", "total_fee")
            )
        );
        
        QueryExecutor.QueryResult result = queryService.executeQuery(query);
        
        // 计算总量（用于百分比）
        long totalCount = result.getRows().stream()
            .mapToLong(r -> ((Number) r.get("count")).longValue())
            .sum();
        
        Map<String, VehicleTypeStats> distribution = new HashMap<>();
        for (Map<String, Object> row : result.getRows()) {
            String vehicleType = String.valueOf(row.get("snapshot_vehicle_type"));
            Integer count = ((Number) row.get("count")).intValue();
            Double totalFee = ((Number) row.get("total_fee")).doubleValue() / 100;
            
            VehicleTypeStats stats = new VehicleTypeStats();
            stats.setVehicleType(vehicleType);
            stats.setCount(count);
            stats.setPercentage(count * 100.0 / totalCount);
            stats.setTotalFee(totalFee);
            
            distribution.put(vehicleType, stats);
        }
        
        return distribution;
    }
    
    /**
     * 计算繁忙度等级
     */
    private BusyLevel calculateBusyLevel(Long totalCount, long days) {
        double avgDaily = totalCount / (double) days;
        
        // 繁忙度评分规则（可配置）
        double score;
        String level;
        
        if (avgDaily < 100) {
            level = "LOW";
            score = (avgDaily / 100) * 33;
        } else if (avgDaily < 500) {
            level = "MEDIUM";
            score = 33 + ((avgDaily - 100) / 400) * 34;
        } else {
            level = "HIGH";
            score = 67 + Math.min(((avgDaily - 500) / 500) * 33, 33);
        }
        
        return new BusyLevel(level, score);
    }
    
    /**
     * 生成车辆画像
     */
    public VehicleProfileData generateVehicleProfile(
        String vehicleId,
        LocalDate startDate,
        LocalDate endDate
    ) throws Exception {
        // 实现逻辑类似门架画像
        // 基于 ExitTransaction 聚合分析
        // ...
    }
}

@Data
@AllArgsConstructor
class BusyLevel {
    private String level;
    private Double score;
}
```

---

#### **3.2.3 前端页面实现**

##### **门架画像页面**

```typescript
// web/src/pages/GantryProfileView.tsx
import React, { useState, useEffect } from 'react';
import { profileApi } from '../api/profile';
import { schemaApi } from '../api/client';

interface GantryProfileData {
  gantryId: string;
  gantryHex: string;
  startDate: string;
  endDate: string;
  totalTransactionCount: number;
  totalRevenue: number;
  avgDailyRevenue: number;
  avgTransactionAmount: number;
  hourlyDistribution: Record<string, number>;
  peakHour: string;
  vehicleTypeDistribution: Record<string, VehicleTypeStats>;
  busyLevel: string;
  busyScore: number;
}

const GantryProfileView: React.FC = () => {
  const [gantries, setGantries] = useState<any[]>([]);
  const [selectedGantryId, setSelectedGantryId] = useState<string>('');
  const [startDate, setStartDate] = useState<string>('2024-01-01');
  const [endDate, setEndDate] = useState<string>('2024-12-31');
  const [profile, setProfile] = useState<GantryProfileData | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 加载门架列表
  useEffect(() => {
    loadGantries();
  }, []);

  const loadGantries = async () => {
    try {
      const instances = await schemaApi.getInstances('Gantry');
      setGantries(instances);
      if (instances.length > 0) {
        setSelectedGantryId(instances[0].id);
      }
    } catch (err) {
      console.error('Failed to load gantries:', err);
    }
  };

  // 加载画像
  const loadProfile = async () => {
    if (!selectedGantryId) return;
    
    setLoading(true);
    setError(null);
    try {
      const data = await profileApi.getGantryProfile(
        selectedGantryId,
        startDate,
        endDate
      );
      setProfile(data);
    } catch (err: any) {
      setError(err.message || '加载画像失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="container mx-auto p-6">
      <h1 className="text-3xl font-bold mb-6">门架画像分析</h1>

      {/* 查询条件 */}
      <div className="bg-white rounded-lg shadow p-6 mb-6">
        <div className="grid grid-cols-4 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              选择门架
            </label>
            <select
              value={selectedGantryId}
              onChange={(e) => setSelectedGantryId(e.target.value)}
              className="w-full p-2 border rounded"
            >
              <option value="">请选择门架</option>
              {gantries.map(g => (
                <option key={g.id} value={g.id}>
                  {g.properties.gantry_hex || g.id}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              开始日期
            </label>
            <input
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              className="w-full p-2 border rounded"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              结束日期
            </label>
            <input
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
              className="w-full p-2 border rounded"
            />
          </div>
          <div className="flex items-end">
            <button
              onClick={loadProfile}
              disabled={loading || !selectedGantryId}
              className="w-full px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 disabled:bg-gray-400"
            >
              {loading ? '加载中...' : '查看画像'}
            </button>
          </div>
        </div>
      </div>

      {/* 错误提示 */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded p-4 mb-6">
          <p className="text-red-800">{error}</p>
        </div>
      )}

      {/* 画像展示 */}
      {profile && (
        <div className="space-y-6">
          {/* 核心指标卡片 */}
          <div className="grid grid-cols-4 gap-4">
            <MetricCard
              title="总交易量"
              value={profile.totalTransactionCount.toLocaleString()}
              unit="笔"
              icon="📊"
            />
            <MetricCard
              title="总收入"
              value={profile.totalRevenue.toLocaleString()}
              unit="元"
              icon="💰"
            />
            <MetricCard
              title="平均单笔金额"
              value={profile.avgTransactionAmount.toFixed(2)}
              unit="元"
              icon="💳"
            />
            <MetricCard
              title="繁忙度"
              value={profile.busyLevel}
              subtitle={`评分: ${profile.busyScore.toFixed(1)}`}
              icon="🔥"
              levelColor={getBusyLevelColor(profile.busyLevel)}
            />
          </div>

          {/* 时间分布图表 */}
          <div className="bg-white rounded-lg shadow p-6">
            <h2 className="text-xl font-semibold mb-4">24小时交易分布</h2>
            <HourlyDistributionChart data={profile.hourlyDistribution} />
            <p className="text-sm text-gray-600 mt-2">
              高峰时段: {profile.peakHour}
            </p>
          </div>

          {/* 车型分布 */}
          <div className="bg-white rounded-lg shadow p-6">
            <h2 className="text-xl font-semibold mb-4">车型分布</h2>
            <VehicleTypeDistributionChart 
              data={profile.vehicleTypeDistribution} 
            />
          </div>
        </div>
      )}
    </div>
  );
};

// 指标卡片组件
const MetricCard: React.FC<{
  title: string;
  value: string;
  unit?: string;
  subtitle?: string;
  icon?: string;
  levelColor?: string;
}> = ({ title, value, unit, subtitle, icon, levelColor }) => (
  <div className="bg-white rounded-lg shadow p-6">
    <div className="flex items-center justify-between mb-2">
      <span className="text-sm text-gray-600">{title}</span>
      {icon && <span className="text-2xl">{icon}</span>}
    </div>
    <div className="flex items-baseline">
      <span 
        className={`text-3xl font-bold ${levelColor || 'text-gray-900'}`}
      >
        {value}
      </span>
      {unit && <span className="ml-2 text-gray-600">{unit}</span>}
    </div>
    {subtitle && (
      <p className="text-xs text-gray-500 mt-1">{subtitle}</p>
    )}
  </div>
);

// 小时分布图表组件（使用 recharts）
const HourlyDistributionChart: React.FC<{
  data: Record<string, number>;
}> = ({ data }) => {
  const chartData = Object.entries(data).map(([hour, count]) => ({
    hour: `${hour}:00`,
    count,
  }));

  return (
    <div className="h-64">
      {/* 使用 recharts BarChart 实现 */}
      <p className="text-sm text-gray-500">
        图表展示：{chartData.length} 个时段数据
      </p>
    </div>
  );
};

// 车型分布饼图
const VehicleTypeDistributionChart: React.FC<{
  data: Record<string, any>;
}> = ({ data }) => {
  return (
    <div className="grid grid-cols-2 gap-4">
      <div className="h-64">
        {/* 饼图 */}
      </div>
      <div>
        {/* 统计表格 */}
        <table className="min-w-full text-sm">
          <thead>
            <tr className="border-b">
              <th className="text-left py-2">车型</th>
              <th className="text-right py-2">数量</th>
              <th className="text-right py-2">占比</th>
            </tr>
          </thead>
          <tbody>
            {Object.entries(data).map(([type, stats]: [string, any]) => (
              <tr key={type} className="border-b">
                <td className="py-2">{type}</td>
                <td className="text-right">{stats.count}</td>
                <td className="text-right">{stats.percentage.toFixed(1)}%</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

// 工具函数
const getBusyLevelColor = (level: string) => {
  switch (level) {
    case 'HIGH': return 'text-red-600';
    case 'MEDIUM': return 'text-yellow-600';
    case 'LOW': return 'text-green-600';
    default: return 'text-gray-600';
  }
};

export default GantryProfileView;
```

##### **API 客户端**

```typescript
// web/src/api/profile.ts
import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export const profileApi = {
  /**
   * 获取门架画像
   */
  getGantryProfile: async (
    gantryId: string,
    startDate: string,
    endDate: string
  ) => {
    const response = await axios.get(
      `${API_BASE_URL}/api/v1/profiles/gantry/${gantryId}`,
      { params: { startDate, endDate } }
    );
    return response.data.data;
  },

  /**
   * 获取车辆画像
   */
  getVehicleProfile: async (
    vehicleId: string,
    startDate: string,
    endDate: string
  ) => {
    const response = await axios.get(
      `${API_BASE_URL}/api/v1/profiles/vehicle/${vehicleId}`,
      { params: { startDate, endDate } }
    );
    return response.data.data;
  },

  /**
   * 批量获取门架画像（用于对比）
   */
  batchGetGantryProfiles: async (
    gantryIds: string[],
    startDate: string,
    endDate: string
  ) => {
    const response = await axios.post(
      `${API_BASE_URL}/api/v1/profiles/gantry/batch`,
      { gantryIds, startDate, endDate }
    );
    return response.data.data;
  },
};
```

---

### 3.3 方案B详细设计（预计算缓存）

#### **3.3.1 定时任务实现**

```java
// src/main/java/com/mypalantir/scheduler/ProfileScheduler.java
package com.mypalantir.scheduler;

@Component
@EnableScheduling
public class ProfileScheduler {
    
    private final ProfileCacheService profileCacheService;
    
    /**
     * 每日凌晨2点更新门架画像缓存
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void updateDailyGantryProfiles() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        
        try {
            profileCacheService.batchUpdateGantryProfiles(
                ProfileType.DAILY,
                yesterday
            );
            log.info("Daily gantry profiles updated for date: {}", yesterday);
        } catch (Exception e) {
            log.error("Failed to update daily gantry profiles", e);
        }
    }
    
    /**
     * 每周一凌晨3点更新周度画像
     */
    @Scheduled(cron = "0 0 3 ? * MON")
    public void updateWeeklyProfiles() {
        LocalDate lastWeekEnd = LocalDate.now().minusWeeks(1);
        LocalDate lastWeekStart = lastWeekEnd.minusDays(6);
        
        profileCacheService.batchUpdateGantryProfiles(
            ProfileType.WEEKLY,
            lastWeekStart,
            lastWeekEnd
        );
    }
}
```

#### **3.3.2 缓存服务实现**

```java
// src/main/java/com/mypalantir/service/ProfileCacheService.java
@Service
public class ProfileCacheService {
    
    private final ProfileService profileService;
    private final ProfileCacheRepository cacheRepository;
    
    /**
     * 批量更新门架画像缓存
     */
    @Transactional
    public void batchUpdateGantryProfiles(
        ProfileType profileType,
        LocalDate date
    ) throws Exception {
        // 获取所有门架
        List<String> gantryIds = getAllGantryIds();
        
        LocalDate startDate = date;
        LocalDate endDate = date;
        
        for (String gantryId : gantryIds) {
            try {
                // 生成画像
                GantryProfileData profile = profileService.generateGantryProfile(
                    gantryId,
                    startDate,
                    endDate
                );
                
                // 保存到缓存
                ProfileCache cache = new ProfileCache();
                cache.setId(UUID.randomUUID().toString());
                cache.setEntityType("Gantry");
                cache.setEntityId(gantryId);
                cache.setProfileType(profileType.name().toLowerCase());
                cache.setProfileDate(date);
                cache.setMetricsJson(JsonUtils.toJson(profile));
                
                cacheRepository.save(cache);
                
            } catch (Exception e) {
                log.error("Failed to update profile for gantry: {}", gantryId, e);
                // 继续处理下一个
            }
        }
    }
    
    /**
     * 从缓存读取画像
     */
    public GantryProfileData getGantryProfileFromCache(
        String gantryId,
        ProfileType profileType,
        LocalDate date
    ) {
        Optional<ProfileCache> cache = cacheRepository.findByEntityAndDate(
            "Gantry",
            gantryId,
            profileType.name().toLowerCase(),
            date
        );
        
        if (cache.isPresent()) {
            return JsonUtils.fromJson(
                cache.get().getMetricsJson(),
                GantryProfileData.class
            );
        }
        
        return null;
    }
}
```

---

## 四、实施路径

### 4.1 分阶段实施

#### **Phase 1: MVP 快速验证（1-2周）**

**目标**：验证画像功能的业务价值

**交付内容**：
1. 后端 ProfileController 和 ProfileService（方案A）
2. 前端门架画像页面（基础版）
3. 3-5 个核心指标展示

**评估指标**：
- 用户访问量
- 页面加载时间（< 3s）
- 用户反馈

---

#### **Phase 2: 功能完善（2-3周）**

**目标**：增强画像维度和交互体验

**交付内容**：
1. 车辆画像页面
2. 图表可视化增强（接入 recharts）
3. 画像对比功能
4. 数据导出功能

---

#### **Phase 3: 性能优化（1-2周）**

**目标**：提升查询性能，支持大规模访问

**交付内容**：
1. 引入缓存机制（方案B）
2. 定时任务批量计算
3. 缓存命中率监控
4. 历史画像趋势分析

---

### 4.2 技术依赖清单

**后端**：
- Spring Boot（已有）
- Metric System（已有）
- Query Service（已有）
- Jackson（JSON处理，已有）

**前端**：
- React + TypeScript（已有）
- Recharts（图表库，需新增）
- Tailwind CSS（已有）

**数据库**：
- H2（测试环境，已有）
- MySQL/PostgreSQL（生产环境）

---

## 五、扩展方向

### 5.1 智能分析

- **异常检测**：基于历史画像识别异常波动
- **预测分析**：预测未来流量和收入趋势
- **智能推荐**：推荐优化策略

### 5.2 标签体系

- **自定义标签**：支持用户自定义画像维度
- **标签管理**：标签的增删改查
- **标签组合**：多标签联合分析

### 5.3 画像应用

- **精准营销**：基于车辆画像的差异化定价
- **资源优化**：基于门架画像的设备维护计划
- **风险控制**：异常通行行为识别

---

## 六、总结

### 6.1 核心优势

1. **架构简洁**：基于现有指标系统，无需重构
2. **实施快速**：方案A可在2周内完成MVP
3. **扩展灵活**：支持按需增加画像维度
4. **性能可控**：方案B提供缓存优化路径

### 6.2 关键成功因素

1. **指标完备性**：画像质量依赖底层指标覆盖度
2. **性能优化**：大数据量场景需引入缓存机制
3. **用户体验**：可视化设计和交互流畅度
4. **业务价值**：画像洞察需转化为实际业务决策

### 6.3 风险与应对

| 风险 | 影响 | 应对措施 |
|------|------|---------|
| 数据量大导致查询慢 | 页面加载超时 | 引入缓存机制（方案B） |
| 指标维度不足 | 画像信息不全 | 先完善指标体系 |
| 前端图表性能差 | 用户体验不佳 | 数据分页 + 虚拟滚动 |
| 历史数据缺失 | 画像不准确 | 设定最小数据量阈值 |

---

## 七、附录

### 7.1 指标清单

**门架画像需要的指标**：
1. 总交易量（原子指标）
2. 总收入（原子指标）
3. 平均单笔金额（派生指标）
4. 按小时聚合的交易量（自定义查询）
5. 按车型聚合的交易量和收入（自定义查询）

**车辆画像需要的指标**：
1. 总通行次数（原子指标）
2. 总里程（原子指标）
3. 总费用（原子指标）
4. 常用路线（自定义查询 + TOP N）
5. 常用收费站（自定义查询 + TOP N）
6. 活跃时段分布（自定义查询）

### 7.2 配置项

```properties
# application.properties

# 画像功能开关
profile.enabled=true

# 缓存策略
profile.cache.enabled=false
profile.cache.ttl=86400

# 繁忙度评级阈值
profile.gantry.busy.low.threshold=100
profile.gantry.busy.medium.threshold=500

# 用户分类阈值（通行次数/月）
profile.vehicle.high-freq.threshold=20
profile.vehicle.medium-freq.threshold=5

# 批量计算配置
profile.batch.size=100
profile.batch.parallel=true
```

### 7.3 参考资料

- [指标系统设计文档](./METRIC_SYSTEM.md)
- [Query Builder 使用指南](./QUERY_BUILDER.md)
- [Recharts 官方文档](https://recharts.org/)

---

**文档版本**: v1.0  
**创建日期**: 2026-01-15  
**作者**: Qoder  
**状态**: 设计阶段
