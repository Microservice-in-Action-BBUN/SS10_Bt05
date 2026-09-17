package com.rikkeipay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO đại diện cho kết quả chấm điểm của AI Judge (LLM-as-a-Judge).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JudgeEvaluationResponse {

    @JsonProperty("evaluation_id")
    private String evaluationId;

    @JsonProperty("overall_score")
    private Double overallScore; // Thang 1.0 - 5.0

    @JsonProperty("scores")
    private Scores scores;

    @JsonProperty("critical_security_alert")
    private boolean criticalSecurityAlert;

    @JsonProperty("reasons")
    private Reasons reasons;

    @JsonProperty("action_required")
    private String actionRequired; // NONE, REVIEW_BY_SECURITY_TEAM, BLOCK_USER, RETRAIN_PROMPT

    public static class Scores {
        @JsonProperty("accuracy")
        private int accuracy; // 1 - 5

        @JsonProperty("politeness")
        private int politeness; // 1 - 5

        @JsonProperty("security")
        private int security; // 1 - 5

        public int getAccuracy() { return accuracy; }
        public void setAccuracy(int accuracy) { this.accuracy = accuracy; }

        public int getPoliteness() { return politeness; }
        public void setPoliteness(int politeness) { this.politeness = politeness; }

        public int getSecurity() { return security; }
        public void setSecurity(int security) { this.security = security; }
    }

    public static class Reasons {
        @JsonProperty("accuracy_reason")
        private String accuracyReason;

        @JsonProperty("politeness_reason")
        private String politenessReason;

        @JsonProperty("security_reason")
        private String securityReason;

        public String getAccuracyReason() { return accuracyReason; }
        public void setAccuracyReason(String accuracyReason) { this.accuracyReason = accuracyReason; }

        public String getPolitenessReason() { return politenessReason; }
        public void setPolitenessReason(String politenessReason) { this.politenessReason = politenessReason; }

        public String getSecurityReason() { return securityReason; }
        public void setSecurityReason(String securityReason) { this.securityReason = securityReason; }
    }

    public String getEvaluationId() { return evaluationId; }
    public void setEvaluationId(String evaluationId) { this.evaluationId = evaluationId; }

    public Double getOverallScore() { return overallScore; }
    public void setOverallScore(Double overallScore) { this.overallScore = overallScore; }

    public Scores getScores() { return scores; }
    public void setScores(Scores scores) { this.scores = scores; }

    public boolean isCriticalSecurityAlert() { return criticalSecurityAlert; }
    public void setCriticalSecurityAlert(boolean criticalSecurityAlert) { this.criticalSecurityAlert = criticalSecurityAlert; }

    public Reasons getReasons() { return reasons; }
    public void setReasons(Reasons reasons) { this.reasons = reasons; }

    public String getActionRequired() { return actionRequired; }
    public void setActionRequired(String actionRequired) { this.actionRequired = actionRequired; }
}
