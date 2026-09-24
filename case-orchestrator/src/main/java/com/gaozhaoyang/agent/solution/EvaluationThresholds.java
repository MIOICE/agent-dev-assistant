package com.gaozhaoyang.agent.solution;

public record EvaluationThresholds(
        double minimumCoverage,
        double minimumAccuracy,
        double minimumMacroRecall,
        double minimumClassRecall,
        double maximumRegression
) {
    public EvaluationThresholds {
        validate("minimumCoverage", minimumCoverage);
        validate("minimumAccuracy", minimumAccuracy);
        validate("minimumMacroRecall", minimumMacroRecall);
        validate("minimumClassRecall", minimumClassRecall);
        validate("maximumRegression", maximumRegression);
    }

    private static void validate(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + "必须在0到1之间");
        }
    }
}
