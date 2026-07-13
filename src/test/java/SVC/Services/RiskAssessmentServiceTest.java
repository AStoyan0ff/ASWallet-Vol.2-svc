package SVC.Services;

import SVC.DTOs.CreateRiskAssessmentRequest;
import SVC.DTOs.ReviewRiskAssessmentRequest;
import SVC.DTOs.RiskAssessmentResponse;
import SVC.Enums.AssessmentStatus;
import SVC.Enums.RiskDecision;
import SVC.Enums.RiskLevel;
import SVC.Exceptions.InvalidReviewStateException;
import SVC.Exceptions.RiskAssessmentNotFoundException;
import SVC.Models.TransferRiskAssessment;
import SVC.Repositories.TransferRiskAssessmentRepository;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskAssessmentServiceTest {

    @Mock private TransferRiskAssessmentRepository repository;
    @Mock private RiskScoringService riskScoringService;

    @InjectMocks
    private RiskAssessmentService riskAssessmentService;

    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().build();
        riskAssessmentService = new RiskAssessmentService(repository, riskScoringService, jsonMapper);
    }

    @Test
    void createAssessment_allowDecision_persistsApprovedStatus() {

        CreateRiskAssessmentRequest request = buildRequest();
        when(riskScoringService.evaluate(request)).thenReturn(
                new RiskScoringService.RiskScoringResult(10, RiskLevel.LOW, RiskDecision.ALLOW, java.util.List.of("OK"))
        );
        when(repository.save(any(TransferRiskAssessment.class))).thenAnswer(invocation -> {
            TransferRiskAssessment saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        RiskAssessmentResponse response = riskAssessmentService.createAssessment(request);

        assertThat(response.getDecision()).isEqualTo(RiskDecision.ALLOW);
        assertThat(response.getStatus()).isEqualTo(AssessmentStatus.APPROVED);

        ArgumentCaptor<TransferRiskAssessment> captor = ArgumentCaptor.forClass(TransferRiskAssessment.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSenderUsername()).isEqualTo("Plamen");
    }

    @Test
    void createAssessment_reviewDecision_persistsPendingStatus() {

        CreateRiskAssessmentRequest request = buildRequest();
        when(riskScoringService.evaluate(request)).thenReturn(
                new RiskScoringService.RiskScoringResult(55, RiskLevel.MEDIUM, RiskDecision.REVIEW, java.util.List.of("Night transfer"))
        );

        when(repository.save(any(TransferRiskAssessment.class))).thenAnswer(invocation -> {
            TransferRiskAssessment saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        RiskAssessmentResponse response = riskAssessmentService.createAssessment(request);

        assertThat(response.getDecision()).isEqualTo(RiskDecision.REVIEW);
        assertThat(response.getStatus()).isEqualTo(AssessmentStatus.PENDING);
        assertThat(response.getReasons()).contains("Night transfer");
    }

    @Test
    void getById_unknownId_throws() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RiskAssessmentNotFoundException.class, () -> riskAssessmentService.getById(id));
    }

    @Test
    void review_pendingAssessment_approvesAndStoresReviewer() {
        UUID id = UUID.randomUUID();

        TransferRiskAssessment entity = TransferRiskAssessment.builder()
                .id(id)
                .senderUsername("Plamen")
                .receiverUsername("Georgi")
                .amount(new BigDecimal("200.00"))
                .riskScore(55)
                .riskLevel(RiskLevel.MEDIUM)
                .decision(RiskDecision.REVIEW)
                .status(AssessmentStatus.PENDING)
                .reasons("[\"Night transfer\"]")
                .build();

        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);

        ReviewRiskAssessmentRequest reviewRequest = new ReviewRiskAssessmentRequest();
        reviewRequest.setStatus(AssessmentStatus.APPROVED);
        reviewRequest.setReviewedBy("admin");

        RiskAssessmentResponse response = riskAssessmentService.review(id, reviewRequest);

        assertThat(response.getStatus()).isEqualTo(AssessmentStatus.APPROVED);
        assertThat(response.getReviewedBy()).isEqualTo("admin");
        assertThat(entity.getReviewedAt()).isNotNull();
    }

    @Test
    void review_nonPendingAssessment_throws() {
        UUID id = UUID.randomUUID();

        TransferRiskAssessment entity = TransferRiskAssessment.builder()
                .id(id)
                .senderUsername("Plamen")
                .receiverUsername("Georgi")
                .amount(new BigDecimal("25.00"))
                .riskScore(10)
                .riskLevel(RiskLevel.LOW)
                .decision(RiskDecision.ALLOW)
                .status(AssessmentStatus.APPROVED)
                .reasons("[\"OK\"]")
                .build();

        when(repository.findById(id)).thenReturn(Optional.of(entity));

        ReviewRiskAssessmentRequest reviewRequest = new ReviewRiskAssessmentRequest();
        reviewRequest.setStatus(AssessmentStatus.APPROVED);
        reviewRequest.setReviewedBy("admin");

        assertThrows(InvalidReviewStateException.class, () -> riskAssessmentService.review(id, reviewRequest));
    }

    @Test
    void deleteAllByStatus_pending_deletesAllPendingAssessments() {

        TransferRiskAssessment pending = TransferRiskAssessment.builder()
                .id(UUID.randomUUID())
                .status(AssessmentStatus.PENDING)
                .build();

        when(repository.findAllByStatusOrderByCreatedAtDesc(AssessmentStatus.PENDING))
                .thenReturn(List.of(pending));

        int deleted = riskAssessmentService.deleteAllByStatus(AssessmentStatus.PENDING);

        assertThat(deleted).isEqualTo(1);
        verify(repository).deleteAll(List.of(pending));
    }

    @Test
    void deleteAllByStatus_nonPending_throws() {

        assertThrows(
                InvalidReviewStateException.class,
                () -> riskAssessmentService.deleteAllByStatus(AssessmentStatus.APPROVED)
        );
        verify(repository, never()).deleteAll(any());
    }

    @Test
    void listByDecision_review_returnsAllManualReviews() {
        TransferRiskAssessment pending = TransferRiskAssessment.builder()
                .id(UUID.randomUUID())
                .decision(RiskDecision.REVIEW)
                .status(AssessmentStatus.PENDING)
                .reasons("[\"Night transfer\"]")
                .build();
        TransferRiskAssessment approved = TransferRiskAssessment.builder()
                .id(UUID.randomUUID())
                .decision(RiskDecision.REVIEW)
                .status(AssessmentStatus.APPROVED)
                .reasons("[\"Night transfer\"]")
                .build();

        when(repository.findAllByDecisionOrderByCreatedAtDesc(RiskDecision.REVIEW))
                .thenReturn(List.of(pending, approved));

        List<RiskAssessmentResponse> responses = riskAssessmentService.listByDecision(RiskDecision.REVIEW);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getDecision()).isEqualTo(RiskDecision.REVIEW);
        assertThat(responses.get(1).getStatus()).isEqualTo(AssessmentStatus.APPROVED);
    }

    @Test
    void deleteAllByDecision_review_deletesAllManualReviews() {
        TransferRiskAssessment pending = TransferRiskAssessment.builder()
                .id(UUID.randomUUID())
                .decision(RiskDecision.REVIEW)
                .status(AssessmentStatus.PENDING)
                .build();
        TransferRiskAssessment rejected = TransferRiskAssessment.builder()
                .id(UUID.randomUUID())
                .decision(RiskDecision.REVIEW)
                .status(AssessmentStatus.REJECTED)
                .build();

        when(repository.findAllByDecisionOrderByCreatedAtDesc(RiskDecision.REVIEW))
                .thenReturn(List.of(pending, rejected));

        int deleted = riskAssessmentService.deleteAllByDecision(RiskDecision.REVIEW);

        assertThat(deleted).isEqualTo(2);
        verify(repository).deleteAll(List.of(pending, rejected));
    }

    private CreateRiskAssessmentRequest buildRequest() {

        CreateRiskAssessmentRequest request = new CreateRiskAssessmentRequest();
        request.setSenderUsername("Plamen");
        request.setReceiverUsername("Georgi");
        request.setAmount(new BigDecimal("25.00"));
        request.setSenderBalance(new BigDecimal("500.00"));
        request.setWithdrawnToday(new BigDecimal("0.00"));
        request.setDailyLimit(new BigDecimal("500.00"));
        request.setTransfersTodayCount(0);
        request.setReceiverHasBankCard(true);
        request.setNewReceiver(false);
        request.setAccountStatus("ACTIVE");
        request.setHourOfDay(14);
        return request;
    }
}
