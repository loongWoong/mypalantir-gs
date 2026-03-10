package com.mypalantir.agent;

import java.util.*;

/**
 * Agent 响应数据类
 */
public class AgentResponse {
    private final String answer;
    private final List<AgentStep> steps;

    public AgentResponse(String answer, List<AgentStep> steps) {
        this.answer = answer;
        this.steps = steps;
    }

    public String getAnswer() { return answer; }
    public List<AgentStep> getSteps() { return steps; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("answer", answer);
        List<Map<String, Object>> stepList = new ArrayList<>();
        for (AgentStep step : steps) {
            stepList.add(step.toMap());
        }
        m.put("steps", stepList);
        return m;
    }

    /**
     * 单步推理记录
     */
    public static class AgentStep {
        private final String thought;
        private final String tool;
        private final Map<String, Object> args;
        private String observation;

        public AgentStep(String thought, String tool, Map<String, Object> args) {
            this.thought = thought;
            this.tool = tool;
            this.args = args;
        }

        public void setObservation(String observation) {
            this.observation = observation;
        }

        public String getThought() { return thought; }
        public String getTool() { return tool; }
        public Map<String, Object> getArgs() { return args; }
        public String getObservation() { return observation; }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            if (thought != null) m.put("thought", thought);
            if (tool != null) m.put("tool", tool);
            if (args != null) m.put("args", args);
            if (observation != null) m.put("observation", observation);
            return m;
        }
    }
}
