# 循环依赖修复说明

## 问题描述

Spring Boot启动时出现循环依赖错误：

```
The dependencies of some of the beans in the application context form a cycle:
  comparisonController -> dataComparisonService -> instanceStorage 
  -> relationalInstanceStorage -> queryService -> instanceStorage
```

## 循环依赖链分析

```
instanceStorage (StorageFactory)
    ↓
relationalInstanceStorage
    ↓
queryService
    ↓
instanceStorage (循环!)
```

## 解决方案

使用 `@Lazy` 注解打破循环依赖，在以下位置添加：

### 1. RelationalInstanceStorage

**文件**: `src/main/java/com/mypalantir/repository/RelationalInstanceStorage.java`

```java
@Autowired
@Lazy
private QueryService queryService;
```

**原因**: `RelationalInstanceStorage` 只在需要查询时才使用 `QueryService`，可以延迟加载。

### 2. QueryService 构造函数

**文件**: `src/main/java/com/mypalantir/service/QueryService.java`

```java
@Autowired
public QueryService(Loader loader, @Lazy IInstanceStorage instanceStorage, ...) {
    // ...
}
```

**原因**: `QueryService` 中的 `instanceStorage` 用于创建 `QueryExecutor` 和 `FederatedCalciteRunner`，这些是延迟初始化的（在第一次查询时才创建）。

### 3. MyPalantirApplication Bean定义

**文件**: `src/main/java/com/mypalantir/MyPalantirApplication.java`

```java
@Bean
public QueryService queryService(@Lazy Loader loader, @Lazy IInstanceStorage instanceStorage, ...) {
    return new QueryService(loader, instanceStorage, ...);
}
```

**原因**: 确保Bean定义时也使用延迟加载。

## @Lazy 注解的工作原理

`@Lazy` 注解告诉Spring：
- 不要立即创建Bean实例
- 只在第一次使用时才创建
- 使用代理对象来延迟初始化

这样就能打破循环依赖，因为：
1. `instanceStorage` 创建时，`queryService` 只是一个代理
2. `relationalInstanceStorage` 创建时，`queryService` 只是一个代理
3. 当真正需要使用 `queryService` 时，才会创建实际实例
4. 此时 `instanceStorage` 已经创建完成，循环被打破

## 验证

修复后，应用应该能够正常启动，不再出现循环依赖错误。

## 注意事项

1. **性能影响**: 使用 `@Lazy` 会有轻微的性能开销（代理对象），但通常可以忽略
2. **初始化顺序**: 延迟加载的Bean在第一次使用时才初始化，需要注意初始化顺序
3. **错误发现**: 延迟加载可能导致某些错误在运行时才发现，而不是启动时

## 替代方案

如果 `@Lazy` 方案不够，可以考虑：

1. **重构代码结构**: 提取公共接口，减少直接依赖
2. **使用Setter注入**: 而不是构造函数注入
3. **使用ApplicationContext**: 手动获取Bean，而不是依赖注入

但对于当前情况，`@Lazy` 是最简单有效的方案。

