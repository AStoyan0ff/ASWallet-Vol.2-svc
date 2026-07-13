package SVC.Controllers;

import SVC.DTOs.CreateRiskAssessmentRequest;
import SVC.DTOs.ReviewRiskAssessmentRequest;
import SVC.DTOs.RiskAssessmentResponse;
import SVC.Enums.AssessmentStatus;
import SVC.Enums.RiskDecision;
import SVC.Services.RiskAssessmentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/risk-assessments")
public class RiskAssessmentController {

    private static final Logger logger = LoggerFactory.getLogger(RiskAssessmentController.class);

    private final RiskAssessmentService riskAssessmentService;

    public RiskAssessmentController(RiskAssessmentService riskAssessmentService) {
        this.riskAssessmentService = riskAssessmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RiskAssessmentResponse createAssessment(@Valid @RequestBody CreateRiskAssessmentRequest request) {
        logger.info(
                "POST /api/risk-assessments: sender={}, receiver={}, amount={}, transactionRef={}",
                request.getSenderUsername(),
                request.getReceiverUsername(),
                request.getAmount(),
                request.getTransactionRef()
        );
        return riskAssessmentService.createAssessment(request);
    }

    @GetMapping("/{id}")
    public RiskAssessmentResponse getAssessment(@PathVariable UUID id) {
        logger.debug("GET /api/risk-assessments/{}", id);
        return riskAssessmentService.getById(id);
    }

    @GetMapping
    public List<RiskAssessmentResponse> listAssessments(
            @RequestParam(required = false) AssessmentStatus status,
            @RequestParam(required = false) RiskDecision decision) {

        if (decision != null) {
            logger.debug("GET /api/risk-assessments?decision={}", decision);
            return riskAssessmentService.listByDecision(decision);
        }

        AssessmentStatus resolvedStatus = status != null
            ? status
            : AssessmentStatus.PENDING;

        logger.debug("GET /api/risk-assessments?status={}", resolvedStatus);
        return riskAssessmentService.listByStatus(resolvedStatus);
    }

    @GetMapping("/manual-reviews")
    public List<RiskAssessmentResponse> listManualReviews() {
        logger.debug("GET /api/risk-assessments/manual-reviews");
        return riskAssessmentService.listByDecision(RiskDecision.REVIEW);
    }

    @PatchMapping("/{id}/review")
    public RiskAssessmentResponse reviewAssessment(
           @PathVariable UUID id,
           @Valid @RequestBody ReviewRiskAssessmentRequest request) {

        logger.info(
                "PATCH /api/risk-assessments/{}/review: status={}, reviewedBy={}",
                id,
                request.getStatus(),
                request.getReviewedBy()
        );
        return riskAssessmentService.review(id, request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAssessments(
            @RequestParam(required = false) AssessmentStatus status,
            @RequestParam(required = false) RiskDecision decision) {

        if (decision != null) {

            logger.info("DELETE /api/risk-assessments?decision={}", decision);
            riskAssessmentService.deleteAllByDecision(decision);
            return;
        }

        if (status == null) {
            throw new IllegalArgumentException("Either status or decision must be provided.");
        }

        logger.info("DELETE /api/risk-assessments?status={}", status);
        riskAssessmentService.deleteAllByStatus(status);
    }

    @DeleteMapping("/manual-reviews")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteManualReviews() {

        logger.info("DELETE /api/risk-assessments/manual-reviews");
        riskAssessmentService.deleteAllByDecision(RiskDecision.REVIEW);
    }
}
