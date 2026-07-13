package SVC.Services;

import SVC.DTOs.CreateRiskAssessmentRequest;
import SVC.DTOs.ReviewRiskAssessmentRequest;
import SVC.DTOs.RiskAssessmentResponse;
import SVC.Enums.AssessmentStatus;
import SVC.Enums.RiskDecision;
import SVC.Enums.RiskDecision;
import SVC.Exceptions.InvalidReviewStateException;
import SVC.Exceptions.RiskAssessmentNotFoundException;
import SVC.Models.TransferRiskAssessment;
import SVC.Repositories.TransferRiskAssessmentRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.math.RoundingMode;

@Service
public class RiskAssessmentService {

    private static final Logger logger = LoggerFactory.getLogger(RiskAssessmentService.class);

    private final TransferRiskAssessmentRepository repository;
    private final RiskScoringService riskScoringService;
    private final JsonMapper jsonMapper;

    public RiskAssessmentService(
            TransferRiskAssessmentRepository repository,
            RiskScoringService riskScoringService,
            JsonMapper jsonMapper
    ) {
        this.repository = repository;
        this.riskScoringService = riskScoringService;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public RiskAssessmentResponse createAssessment(CreateRiskAssessmentRequest request) {
        RiskScoringService.RiskScoringResult scoring = riskScoringService.evaluate(request);

        TransferRiskAssessment assessment = TransferRiskAssessment.builder()
                .transactionRef(request.getTransactionRef())
                .senderUsername(request.getSenderUsername().trim())
                .receiverUsername(request.getReceiverUsername().trim())
                .amount(request.getAmount().setScale(2, RoundingMode.HALF_UP))
                .riskScore(scoring.getScore())
                .riskLevel(scoring.getLevel())
                .decision(scoring.getDecision())
                .status(resolveInitialStatus(scoring.getDecision()))
                .reasons(writeReasons(scoring.getReasons()))
                .build();

        TransferRiskAssessment saved = repository.save(assessment);
        logger.info(
                "Risk assessment created: id={}, transactionRef={}, sender={}, receiver={}, amount={}, score={}, decision={}, status={}",
                saved.getId(),
                saved.getTransactionRef(),
                saved.getSenderUsername(),
                saved.getReceiverUsername(),
                saved.getAmount(),
                saved.getRiskScore(),
                saved.getDecision(),
                saved.getStatus()
        );

        return toResponse(saved);
    }

    public RiskAssessmentResponse getById(UUID id) {
        logger.debug("Loading risk assessment by id={}", id);
        return toResponse(findEntity(id));
    }

    public List<RiskAssessmentResponse> listByStatus(AssessmentStatus status) {

        List<RiskAssessmentResponse> assessments = repository.findAllByStatusOrderByCreatedAtDesc(status)
                .stream()
                .map(this::toResponse)
                .toList();
        logger.debug("Listed {} risk assessment(s) with status={}", assessments.size(), status);
        return assessments;
    }

    public List<RiskAssessmentResponse> listByDecision(RiskDecision decision) {

        List<RiskAssessmentResponse> assessments = repository.findAllByDecisionOrderByCreatedAtDesc(decision)
                .stream()
                .map(this::toResponse)
                .toList();
        logger.debug("Listed {} risk assessment(s) with decision={}", assessments.size(), decision);
        return assessments;
    }

    @Transactional
    public RiskAssessmentResponse review(UUID id, ReviewRiskAssessmentRequest request) {
        TransferRiskAssessment assessment = findEntity(id);

        if (assessment.getStatus() != AssessmentStatus.PENDING) {
            throw new InvalidReviewStateException("Only pending assessments can be reviewed.");
        }

        if (assessment.getDecision() != RiskDecision.REVIEW) {
            throw new InvalidReviewStateException("Only assessments marked for review can be manually reviewed.");
        }

        if (request.getStatus() == AssessmentStatus.PENDING) {
            throw new InvalidReviewStateException("Review status must be APPROVED or REJECTED.");
        }

        assessment.setStatus(request.getStatus());
        assessment.setReviewedBy(request.getReviewedBy().trim());
        assessment.setReviewedAt(LocalDateTime.now());

        TransferRiskAssessment saved = repository.save(assessment);
        logger.info(
                "Risk assessment reviewed: id={}, transactionRef={}, status={}, reviewedBy={}",
                saved.getId(),
                saved.getTransactionRef(),
                saved.getStatus(),
                saved.getReviewedBy()
        );

        return toResponse(saved);
    }

    @Transactional
    public int deleteAllByStatus(AssessmentStatus status) {

        if (status != AssessmentStatus.PENDING) {
            throw new InvalidReviewStateException("Only pending assessments can be deleted.");
        }

        List<TransferRiskAssessment> pending = repository.findAllByStatusOrderByCreatedAtDesc(status);

        if (pending.isEmpty()) {
            logger.info("No risk assessments to delete for status={}", status);
            return 0;
        }

        repository.deleteAll(pending);
        logger.info("Deleted {} risk assessment(s) with status={}", pending.size(), status);
        return pending.size();
    }

    @Transactional
    public int deleteAllByDecision(RiskDecision decision) {

        List<TransferRiskAssessment> assessments = repository.findAllByDecisionOrderByCreatedAtDesc(decision);

        if (assessments.isEmpty()) {

            logger.info("No risk assessments to delete for decision={}", decision);
            return 0;
        }

        repository.deleteAll(assessments);
        logger.info("Deleted {} risk assessment(s) with decision={}", assessments.size(), decision);

        return assessments.size();
    }

    private AssessmentStatus resolveInitialStatus(RiskDecision decision) {

        return switch (decision) {
            
            case ALLOW -> AssessmentStatus.APPROVED;
            case REVIEW -> AssessmentStatus.PENDING;
            case BLOCK -> AssessmentStatus.REJECTED;
        };
    }

    private TransferRiskAssessment findEntity(UUID id) {

        return repository.findById(id).orElseThrow(() ->
                new RiskAssessmentNotFoundException("Risk assessment not found."));

    }

    private String writeReasons(List<String> reasons) {

        try {
            return jsonMapper.writeValueAsString(reasons);

        } catch (JacksonException ex) {
            throw new IllegalStateException("Unable to serialize risk reasons.", ex);
        }
    }

    private List<String> readReasons(String reasonsJson) {

        try {
            return jsonMapper.readValue(reasonsJson, new TypeReference<>() {});

        } catch (JacksonException ex) {
            return List.of(reasonsJson);
        }
    }

    private RiskAssessmentResponse toResponse(TransferRiskAssessment assessment) {

        return RiskAssessmentResponse.builder()
                .id(assessment.getId())
                .transactionRef(assessment.getTransactionRef())
                .senderUsername(assessment.getSenderUsername())
                .receiverUsername(assessment.getReceiverUsername())
                .amount(assessment.getAmount())
                .riskScore(assessment.getRiskScore())
                .riskLevel(assessment.getRiskLevel())
                .decision(assessment.getDecision())
                .status(assessment.getStatus())
                .reasons(readReasons(assessment.getReasons()))
                .reviewedBy(assessment.getReviewedBy())
                .reviewedAt(assessment.getReviewedAt())
                .createdAt(assessment.getCreatedAt())
                .build();
    }
}
