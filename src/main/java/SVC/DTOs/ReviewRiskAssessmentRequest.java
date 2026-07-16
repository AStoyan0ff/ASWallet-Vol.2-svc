package SVC.DTOs;

import SVC.Enums.AssessmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReviewRiskAssessmentRequest {

    @NotNull(message = "Status is required.")
    private AssessmentStatus status;

    @NotBlank(message = "Reviewer username is required.")
    @Size(max = 30)
    private String reviewedBy;
}
