package SVC.Services;

import SVC.DTOs.CreateRiskAssessmentRequest;
import SVC.Enums.RiskDecision;
import SVC.Enums.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RiskScoringServiceTest {

    private RiskScoringService riskScoringService;

    @BeforeEach
    void setUp() {
        riskScoringService = new RiskScoringService(40, 70);
    }

    @Test
    void evaluate_lowRiskTransfer_returnsAllow() {
        RiskScoringService.RiskScoringResult result = riskScoringService.evaluate(lowRiskRequest());

        assertThat(result.getScore()).isLessThan(40);
        assertThat(result.getLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(result.getDecision()).isEqualTo(RiskDecision.ALLOW);
    }

    @Test
    void evaluate_highAmountVsBalance_addsReviewRisk() {
        CreateRiskAssessmentRequest request = lowRiskRequest();
        request.setAmount(new BigDecimal("90.00"));
        request.setSenderBalance(new BigDecimal("100.00"));

        RiskScoringService.RiskScoringResult result = riskScoringService.evaluate(request);

        assertThat(result.getScore()).isGreaterThanOrEqualTo(30);
        assertThat(result.getReasons()).anyMatch(reason -> reason.contains("80%"));
    }

    @Test
    void evaluate_exceedsDailyLimit_addsStrongRisk() {
        CreateRiskAssessmentRequest request = lowRiskRequest();
        request.setAmount(new BigDecimal("200.00"));
        request.setWithdrawnToday(new BigDecimal("350.00"));
        request.setDailyLimit(new BigDecimal("500.00"));

        RiskScoringService.RiskScoringResult result = riskScoringService.evaluate(request);

        assertThat(result.getScore()).isGreaterThanOrEqualTo(40);
        assertThat(result.getDecision()).isIn(RiskDecision.REVIEW, RiskDecision.BLOCK);
    }

    @Test
    void evaluate_multipleIndicators_returnsBlock() {
        CreateRiskAssessmentRequest request = lowRiskRequest();
        request.setAmount(new BigDecimal("450.00"));
        request.setSenderBalance(new BigDecimal("500.00"));
        request.setWithdrawnToday(new BigDecimal("100.00"));
        request.setDailyLimit(new BigDecimal("500.00"));
        request.setTransfersTodayCount(3);
        request.setHourOfDay(23);
        request.setNewReceiver(true);
        request.setReceiverHasBankCard(false);

        RiskScoringService.RiskScoringResult result = riskScoringService.evaluate(request);

        assertThat(result.getScore()).isGreaterThanOrEqualTo(70);
        assertThat(result.getDecision()).isEqualTo(RiskDecision.BLOCK);
        assertThat(result.getLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void evaluate_inactiveAccount_blocksImmediately() {
        CreateRiskAssessmentRequest request = lowRiskRequest();
        request.setAccountStatus("INACTIVE");

        RiskScoringService.RiskScoringResult result = riskScoringService.evaluate(request);

        assertThat(result.getScore()).isEqualTo(100);
        assertThat(result.getDecision()).isEqualTo(RiskDecision.BLOCK);
    }

    private CreateRiskAssessmentRequest lowRiskRequest() {
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
