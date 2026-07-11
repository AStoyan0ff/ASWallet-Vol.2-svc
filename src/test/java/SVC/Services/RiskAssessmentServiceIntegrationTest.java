package SVC.Services;

import SVC.DTOs.CreateRiskAssessmentRequest;
import SVC.DTOs.ReviewRiskAssessmentRequest;
import SVC.DTOs.RiskAssessmentResponse;
import SVC.Enums.AssessmentStatus;
import SVC.Enums.RiskDecision;
import SVC.Models.TransferRiskAssessment;
import SVC.Repositories.TransferRiskAssessmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RiskAssessmentServiceIntegrationTest {

    @Autowired
    private RiskAssessmentService riskAssessmentService;

    @Autowired
    private TransferRiskAssessmentRepository repository;

    @Test
    void createReviewAndLoadFromDatabase() {
        UUID transactionRef = UUID.randomUUID();
        CreateRiskAssessmentRequest request = buildReviewRequest(transactionRef);

        RiskAssessmentResponse created = riskAssessmentService.createAssessment(request);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getDecision()).isEqualTo(RiskDecision.REVIEW);
        assertThat(created.getStatus()).isEqualTo(AssessmentStatus.PENDING);
        assertThat(created.getTransactionRef()).isEqualTo(transactionRef);

        TransferRiskAssessment persisted = repository.findById(created.getId()).orElseThrow();
        assertThat(persisted.getSenderUsername()).isEqualTo("Plamen");
        assertThat(persisted.getReceiverUsername()).isEqualTo("Georgi");
        assertThat(persisted.getRiskScore()).isGreaterThanOrEqualTo(40);

        ReviewRiskAssessmentRequest reviewRequest = new ReviewRiskAssessmentRequest();
        reviewRequest.setStatus(AssessmentStatus.APPROVED);
        reviewRequest.setReviewedBy("admin");

        RiskAssessmentResponse reviewed = riskAssessmentService.review(created.getId(), reviewRequest);

        assertThat(reviewed.getStatus()).isEqualTo(AssessmentStatus.APPROVED);
        assertThat(reviewed.getReviewedBy()).isEqualTo("admin");
        assertThat(repository.findById(created.getId()).orElseThrow().getReviewedAt()).isNotNull();
    }

    @Test
    void deleteAllPending_removesRowsFromDatabase() {
        riskAssessmentService.createAssessment(buildReviewRequest(UUID.randomUUID()));

        assertThat(repository.findAllByStatusOrderByCreatedAtDesc(AssessmentStatus.PENDING)).hasSize(1);

        int deleted = riskAssessmentService.deleteAllByStatus(AssessmentStatus.PENDING);

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findAllByStatusOrderByCreatedAtDesc(AssessmentStatus.PENDING)).isEmpty();
    }

    private CreateRiskAssessmentRequest buildReviewRequest(UUID transactionRef) {
        CreateRiskAssessmentRequest request = new CreateRiskAssessmentRequest();
        request.setTransactionRef(transactionRef);
        request.setSenderUsername("Plamen");
        request.setReceiverUsername("Georgi");
        request.setAmount(new BigDecimal("25.00"));
        request.setSenderBalance(new BigDecimal("500.00"));
        request.setWithdrawnToday(new BigDecimal("0.00"));
        request.setDailyLimit(new BigDecimal("500.00"));
        request.setTransfersTodayCount(3);
        request.setReceiverHasBankCard(true);
        request.setNewReceiver(true);
        request.setAccountStatus("ACTIVE");
        request.setHourOfDay(23);
        return request;
    }
}
