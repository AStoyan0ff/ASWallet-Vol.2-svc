package SVC.Controllers;

import SVC.DTOs.RiskAssessmentResponse;
import SVC.Enums.AssessmentStatus;
import SVC.Enums.RiskDecision;
import SVC.Enums.RiskLevel;
import SVC.Exceptions.InvalidReviewStateException;
import SVC.GlobalExceptionHandler.GlobalExceptionHandler;
import SVC.Services.RiskAssessmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RiskAssessmentController.class)
@Import(GlobalExceptionHandler.class)
class RiskAssessmentControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RiskAssessmentService riskAssessmentService;

    @Test
    void postAssessment_returnsCreatedResponse() throws Exception {
        UUID id = UUID.randomUUID();

        RiskAssessmentResponse response = RiskAssessmentResponse.builder()
            .id(id)
            .senderUsername("Plamen")
            .receiverUsername("Georgi")
            .amount(new BigDecimal("25.00"))
            .riskScore(10)
            .riskLevel(RiskLevel.LOW)
            .decision(RiskDecision.ALLOW)
            .status(AssessmentStatus.APPROVED)
            .reasons(List.of("No elevated risk indicators detected."))
            .createdAt(LocalDateTime.now())
            .build();

        when(riskAssessmentService.createAssessment(any())).thenReturn(response);

        mockMvc
            .perform(post("/api/risk-assessments")
                .contentType(MediaType.APPLICATION_JSON)
                .content
                    ("""
                            {
                                "senderUsername": "Plamen",
                                "receiverUsername": "Georgi",
                                "amount": 25.00,
                                "senderBalance": 500.00,
                                "withdrawnToday": 0.00,
                                "dailyLimit": 500.00,
                                "transfersTodayCount": 0,
                                "receiverHasBankCard": true,
                                "newReceiver": false,
                                "accountStatus": "ACTIVE",
                                "hourOfDay": 14
                            }
                    """))

            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.decision").value("ALLOW"))
            .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void postAssessment_validationError_returnsBadRequest() throws Exception {
        mockMvc
            .perform(post("/api/risk-assessments")
            .contentType(MediaType.APPLICATION_JSON)
            .content
                ("""
                    {
                        "senderUsername": "",
                        "receiverUsername": "Georgi",
                        "amount": 0,
                        "senderBalance": 500.00,
                        "withdrawnToday": 0.00,
                        "dailyLimit": 500.00,
                        "accountStatus": "ACTIVE",
                        "hourOfDay": 14
                    }
                """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.senderUsername").exists());
    }

    @Test
    void getAssessment_returnsResponse() throws Exception {
        UUID id = UUID.randomUUID();

        when(riskAssessmentService.getById(id)).thenReturn(
            RiskAssessmentResponse.builder()
                .id(id)
                .senderUsername("Plamen")
                .receiverUsername("Georgi")
                .amount(new BigDecimal("25.00"))
                .riskScore(55)
                .riskLevel(RiskLevel.MEDIUM)
                .decision(RiskDecision.REVIEW)
                .status(AssessmentStatus.PENDING)
                .reasons(List.of("Night transfer"))
                .build()
        );

        mockMvc
            .perform(get("/api/risk-assessments/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decision").value("REVIEW"))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void listAssessments_returnsPendingItems() throws Exception {
        when(riskAssessmentService.listByStatus(AssessmentStatus.PENDING)).thenReturn(List.of(

            RiskAssessmentResponse.builder()
                .id(UUID.randomUUID())
                .senderUsername("Plamen")
                .receiverUsername("Georgi")
                .amount(new BigDecimal("200.00"))
                .riskScore(55)
                .riskLevel(RiskLevel.MEDIUM)
                .decision(RiskDecision.REVIEW)
                .status(AssessmentStatus.PENDING)
                .reasons(List.of("Night transfer"))
                .build()
        ));

        mockMvc
            .perform(get("/api/risk-assessments").param("status", "PENDING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void patchReview_returnsUpdatedAssessment() throws Exception {
        UUID id = UUID.randomUUID();

        when(riskAssessmentService.review(eq(id), any())).thenReturn(
            RiskAssessmentResponse.builder()
                .id(id)
                .senderUsername("Plamen")
                .receiverUsername("Georgi")
                .amount(new BigDecimal("200.00"))
                .riskScore(55)
                .riskLevel(RiskLevel.MEDIUM)
                .decision(RiskDecision.REVIEW)
                .status(AssessmentStatus.APPROVED)
                .reviewedBy("admin")
                .reasons(List.of("Night transfer"))
                .build()
        );

        mockMvc
            .perform(patch("/api/risk-assessments/{id}/review", id)
            .contentType(MediaType.APPLICATION_JSON)
            .content
                ("""
                    {
                        "status": "APPROVED",
                        "reviewedBy": "admin"
                    }
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.reviewedBy").value("admin"));

        verify(riskAssessmentService).review(eq(id), any());
    }

    @Test
    void patchReview_invalidState_returnsConflict() throws Exception {
        UUID id = UUID.randomUUID();

        doThrow(new InvalidReviewStateException("Only pending assessments can be reviewed."))
            .when(riskAssessmentService).review(eq(id), any());

        mockMvc
            .perform(patch("/api/risk-assessments/{id}/review", id)
            .contentType(MediaType.APPLICATION_JSON)
            .content
                ("""
                    {
                        "status": "APPROVED",
                        "reviewedBy": "admin"
                    }
                """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("Only pending assessments can be reviewed."));

        verify(riskAssessmentService).review(eq(id), any());
    }

    @Test
    void deleteAssessments_returnsNoContent() throws Exception {

        mockMvc
            .perform(delete("/api/risk-assessments")
            .param("status", "PENDING"))
            .andExpect(status().isNoContent());

        verify(riskAssessmentService).deleteAllByStatus(AssessmentStatus.PENDING);
    }
}
