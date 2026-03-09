package com.mypalantir.reasoning;

import com.mypalantir.meta.Loader;
import com.mypalantir.meta.OntologySchema;
import com.mypalantir.reasoning.engine.Fact;
import com.mypalantir.reasoning.engine.ForwardChainEngine;
import com.mypalantir.reasoning.engine.InferenceResult;
import com.mypalantir.reasoning.function.FunctionRegistry;
import com.mypalantir.reasoning.function.builtin.*;
import com.mypalantir.reasoning.swrl.SWRLParser;
import com.mypalantir.reasoning.swrl.SWRLRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ForwardChainEngineTest {

    private ForwardChainEngine engine;
    private FunctionRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        registry = new FunctionRegistry();
        registry.register(new IsSingleProvinceEtc());
        registry.register(new IsObuBillingMode1());
        registry.register(new CheckRouteConsistency());
        registry.register(new DetectDuplicateIntervals());
        registry.register(new CheckGantryHexContinuity());
        registry.register(new CheckGantryCountComplete());
        registry.register(new DetectLateUpload());
        registry.register(new CheckFeeDetailConsistency());
        registry.register(new CheckRoundingMismatch());
        registry.register(new CheckBalanceContinuity());

        Loader loader = new Loader("./ontology/toll.yaml");
        loader.load();
        OntologySchema schema = loader.getSchema();
        SWRLParser parser = new SWRLParser(schema, registry);
        List<SWRLRule> rules = parser.parseAll(schema);

        engine = new ForwardChainEngine(registry, rules);
    }

    @Test
    void testBasicInference_Normal() {
        // 模拟一个正常的 Passage：明细数量匹配、收费单元匹配、费用匹配
        Map<String, Object> passage = new HashMap<>();
        passage.put("id", "test-passage-1");

        // 衍生属性（由 CEL 预计算）
        Map<String, Object> derived = new HashMap<>();
        derived.put("detail_count_matched", true);
        derived.put("interval_set_matched", true);
        derived.put("fee_matched", true);

        InferenceResult result = engine.infer(passage, Map.of(), derived);

        System.out.println("=== Normal Passage Inference ===");
        System.out.println("Cycles: " + result.getCycleCount());
        for (InferenceResult.TraceEntry entry : result.getTrace()) {
            System.out.println("  Cycle " + entry.cycle() + ": " + entry.ruleName() + " → " + entry.fact());
        }

        // 应该触发 passage_integrity_normal 规则
        boolean hasNormalStatus = result.getProducedFacts().stream()
            .anyMatch(f -> "check_status".equals(f.getPredicate()) && "正常".equals(f.getValue()));
        assertTrue(hasNormalStatus, "Should produce check_status(?p, '正常')");
    }

    @Test
    void testBasicInference_CountMismatch() {
        Map<String, Object> passage = new HashMap<>();
        passage.put("id", "test-passage-2");

        Map<String, Object> derived = new HashMap<>();
        derived.put("detail_count_matched", false);
        derived.put("interval_set_matched", true);
        derived.put("fee_matched", true);

        InferenceResult result = engine.infer(passage, Map.of(), derived);

        System.out.println("=== Count Mismatch Inference ===");
        for (InferenceResult.TraceEntry entry : result.getTrace()) {
            System.out.println("  Cycle " + entry.cycle() + ": " + entry.ruleName() + " → " + entry.fact());
        }

        // 应该触发 passage_count_mismatch 规则
        boolean hasAbnormal = result.getProducedFacts().stream()
            .anyMatch(f -> "check_status".equals(f.getPredicate()) && "不正常".equals(f.getValue()));
        assertTrue(hasAbnormal, "Should produce check_status(?p, '不正常')");
    }

    @Test
    void testOBUDiagnosis_RouteInconsistency_ETCIncomplete() {
        // 模拟 OBU 拆分路径不一致 + ETC门架不完整 的场景
        Map<String, Object> passage = new HashMap<>();
        passage.put("id", "test-obu-1");
        // 模拟关联的 ExitTransaction 和 Media（供 scope 函数使用）
        passage.put("_exit_transaction", Map.of("multi_province", 0, "pay_type", 1));
        passage.put("_media", Map.of("card_net", "3701", "media_type", 1));

        // 构造门架交易数据：路径不一致（门架有但拆分中没有的 interval）
        List<Map<String, Object>> gantryTxs = List.of(
            Map.of("toll_interval_id", "INT001", "gantry_order_num", 1,
                    "snapshot_gantry_hex", "A1", "snapshot_last_gantry_hex", "",
                    "fee", 100, "pay_fee", 100, "balance_before", 1000, "balance_after", 900),
            Map.of("toll_interval_id", "INT003", "gantry_order_num", 2,
                    "snapshot_gantry_hex", "A3", "snapshot_last_gantry_hex", "A2",  // 不连续！A1→A2 缺失
                    "fee", 200, "pay_fee", 200, "balance_before", 900, "balance_after", 700)
        );

        List<Map<String, Object>> splitDetails = List.of(
            Map.of("interval_id", "INT001", "toll_interval_fee", 100.0),
            Map.of("interval_id", "INT002", "toll_interval_fee", 150.0)  // INT002 不在门架中
        );

        Map<String, List<Map<String, Object>>> linkedData = new HashMap<>();
        linkedData.put("passage_has_gantry_transactions", gantryTxs);
        linkedData.put("passage_has_split_details", splitDetails);
        linkedData.put("passage_has_details", List.of());

        Map<String, Object> derived = new HashMap<>();

        InferenceResult result = engine.infer(passage, linkedData, derived);

        System.out.println("=== OBU Route Inconsistency - ETC Incomplete ===");
        System.out.println("Cycles: " + result.getCycleCount());
        for (InferenceResult.TraceEntry entry : result.getTrace()) {
            System.out.println("  Cycle " + entry.cycle() + ": " + entry.ruleName() + " → " + entry.fact());
        }

        // 验证推理链
        boolean hasScope = result.getProducedFacts().stream()
            .anyMatch(f -> "in_obu_split_scope".equals(f.getPredicate()) && Boolean.TRUE.equals(f.getValue()));
        boolean hasRouteMismatch = result.getProducedFacts().stream()
            .anyMatch(f -> "obu_split_status".equals(f.getPredicate()) && "路径不一致".equals(f.getValue()));
        boolean hasETCIncomplete = result.getProducedFacts().stream()
            .anyMatch(f -> "obu_route_cause".equals(f.getPredicate()) && "ETC门架不完整".equals(f.getValue()));

        assertTrue(hasScope, "Should produce in_obu_split_scope(?p, true)");
        assertTrue(hasRouteMismatch, "Should produce obu_split_status(?p, '路径不一致')");
        assertTrue(hasETCIncomplete, "Should produce obu_route_cause(?p, 'ETC门架不完整')");
    }
}
