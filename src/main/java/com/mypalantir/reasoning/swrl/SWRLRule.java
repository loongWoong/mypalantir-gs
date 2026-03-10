package com.mypalantir.reasoning.swrl;

import java.util.List;

/**
 * 解析后的 SWRL 规则结构。
 * 前件（antecedent）：一组原子条件，全部满足时触发。
 * 后件（consequent）：触发后产生的事实。
 */
public class SWRLRule {
    private final String name;
    private final String displayName;
    private final List<Atom> antecedents;
    private final Atom consequent;

    public SWRLRule(String name, String displayName, List<Atom> antecedents, Atom consequent) {
        this.name = name;
        this.displayName = displayName;
        this.antecedents = antecedents;
        this.consequent = consequent;
    }

    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public List<Atom> getAntecedents() { return antecedents; }
    public Atom getConsequent() { return consequent; }

    @Override
    public String toString() {
        return name + ": " + antecedents + " → " + consequent;
    }
}
