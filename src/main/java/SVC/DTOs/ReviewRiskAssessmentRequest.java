package SVC.DTOs;

import SVC.Enums.AssessmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReviewRiskAssessmentRequest {

    @NotNull(message = "Status is required.")
    private AssessmentStatus status;

    @NotNull(message = "Reviewer username is required.")
    private String reviewedBy;
}
