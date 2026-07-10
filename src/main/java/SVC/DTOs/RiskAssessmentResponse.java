package SVC.DTOs;

import SVC.Enums.AssessmentStatus;
import SVC.Enums.RiskDecision;
import SVC.Enums.RiskLevel;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class RiskAssessmentResponse {

    private UUID id;
    private UUID transactionRef;
    private String senderUsername;
    private String receiverUsername;
    private BigDecimal amount;
    private int riskScore;
    private RiskLevel riskLevel;
    private RiskDecision decision;
    private AssessmentStatus status;
    private List<String> reasons;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
