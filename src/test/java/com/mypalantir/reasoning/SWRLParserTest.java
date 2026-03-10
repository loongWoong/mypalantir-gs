package com.mypalantir.reasoning;

import com.mypalantir.meta.Loader;
import com.mypalantir.meta.OntologySchema;
import com.mypalantir.reasoning.function.FunctionRegistry;
import com.mypalantir.reasoning.swrl.Atom;
import com.mypalantir.reasoning.swrl.SWRLParser;
import com.mypalantir.reasoning.swrl.SWRLRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SWRLParserTest {

    private SWRLParser parser;
    private OntologySchema schema;

    @BeforeEach
    void setUp() throws Exception {
        Loader loader = new Loader("./ontology/toll.yaml");
        loader.load();
        schema = loader.getSchema();
        FunctionRegistry registry = new FunctionRegistry();
        parser = new SWRLParser(schema, registry);
    }

    @Test
    void testParseAllRules() {
        List<SWRLRule> rules = parser.parseAll(schema);
        assertFalse(rules.isEmpty(), "Should parse at least one rule");
        System.out.println("Parsed " + rules.size() + " rules:");
        for (SWRLRule rule : rules) {
            System.out.println("  " + rule.getName() + ": " + rule.getAntecedents().size() + " antecedents");
        }
    }

    @Test
    void testParseSimpleRule() {
        String expr = "Passage(?p) ∧ detail_count_matched(?p, true) ∧ interval_set_matched(?p, true) ∧ fee_matched(?p, true) → check_status(?p, \"正常\")";
        SWRLRule rule = parser.parse("test_rule", "Test Rule", expr);

        assertEquals("test_rule", rule.getName());
        assertEquals(4, rule.getAntecedents().size());

        // First atom: type assertion
        assertEquals(Atom.Type.TYPE_ASSERTION, rule.getAntecedents().get(0).getType());
        assertEquals("Passage", rule.getAntecedents().get(0).getTypeName());

        // Second atom: property match
        assertEquals(Atom.Type.PROPERTY_MATCH, rule.getAntecedents().get(1).getType());
        assertEquals("detail_count_matched", rule.getAntecedents().get(1).getPredicate());
        assertEquals(Boolean.TRUE, rule.getAntecedents().get(1).getValue());

        // Consequent
        assertEquals(Atom.Type.FACT_ASSERTION, rule.getConsequent().getType());
        assertEquals("check_status", rule.getConsequent().getPredicate());
        assertEquals("正常", rule.getConsequent().getValue());
    }

    @Test
    void testParseFunctionCallRule() {
        String expr = "Passage(?p) ∧ is_single_province_etc(?p) == true → in_obu_split_scope(?p, true)";
        SWRLRule rule = parser.parse("scope_rule", "Scope Rule", expr);

        assertEquals(2, rule.getAntecedents().size());

        // Function call atom
        Atom funcAtom = rule.getAntecedents().get(1);
        assertEquals(Atom.Type.FUNCTION_CALL, funcAtom.getType());
        assertEquals("is_single_province_etc", funcAtom.getFunctionName());
        assertEquals(Boolean.TRUE, funcAtom.getExpectedValue());
    }

    @Test
    void testParseDisjunctionRule() {
        String expr = "Passage(?p) ∧ (obu_route_cause(?p, \"ETC门架不完整\") ∨ obu_route_cause(?p, \"CPC门架不完整\")) → obu_incomplete_reason(?p, \"门架延迟上传\")";
        SWRLRule rule = parser.parse("disjunction_rule", "Disjunction Rule", expr);

        // Find the disjunction atom
        Atom disjAtom = rule.getAntecedents().stream()
            .filter(a -> a.getType() == Atom.Type.DISJUNCTION)
            .findFirst()
            .orElse(null);

        assertNotNull(disjAtom);
        assertEquals(2, disjAtom.getDisjuncts().size());
    }

    @Test
    void testParseInequalityRule() {
        String expr = "Passage(?p) ∧ obu_split_status(?p, ?status) ∧ (?status != \"正常\") ∧ entry_involves_vehicle(?p, ?v) → has_obu_split_abnormal(?v, true)";
        SWRLRule rule = parser.parse("inequality_rule", "Inequality Rule", expr);

        // Find inequality atom
        Atom ineqAtom = rule.getAntecedents().stream()
            .filter(a -> a.getType() == Atom.Type.INEQUALITY)
            .findFirst()
            .orElse(null);

        assertNotNull(ineqAtom);
        assertEquals("?status", ineqAtom.getInequalityVar());
        assertEquals("正常", ineqAtom.getInequalityValue());

        // Find link traversal
        Atom linkAtom = rule.getAntecedents().stream()
            .filter(a -> a.getType() == Atom.Type.LINK_TRAVERSAL)
            .findFirst()
            .orElse(null);

        assertNotNull(linkAtom);
        assertEquals("entry_involves_vehicle", linkAtom.getLinkName());
    }
}
