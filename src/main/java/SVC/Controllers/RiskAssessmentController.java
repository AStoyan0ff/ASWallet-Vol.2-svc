package SVC.Controllers;

import SVC.DTOs.CreateRiskAssessmentRequest;
import SVC.DTOs.ReviewRiskAssessmentRequest;
import SVC.DTOs.RiskAssessmentResponse;
import SVC.Enums.AssessmentStatus;
import SVC.Services.RiskAssessmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/risk-assessments")
public class RiskAssessmentController {

    private final RiskAssessmentService riskAssessmentService;

    public RiskAssessmentController(RiskAssessmentService riskAssessmentService) {
        this.riskAssessmentService = riskAssessmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RiskAssessmentResponse createAssessment(@Valid @RequestBody CreateRiskAssessmentRequest request) {
        return riskAssessmentService.createAssessment(request);
    }

    @GetMapping("/{id}")
    public RiskAssessmentResponse getAssessment(@PathVariable UUID id) {
        return riskAssessmentService.getById(id);
    }

    @GetMapping
    public List<RiskAssessmentResponse> listAssessments(
            @RequestParam(defaultValue = "PENDING") AssessmentStatus status) {

        return riskAssessmentService.listByStatus(status);
    }

    @PatchMapping("/{id}/review")
    public RiskAssessmentResponse reviewAssessment(
           @PathVariable UUID id,
           @Valid @RequestBody ReviewRiskAssessmentRequest request) {

        return riskAssessmentService.review(id, request);
    }
}
