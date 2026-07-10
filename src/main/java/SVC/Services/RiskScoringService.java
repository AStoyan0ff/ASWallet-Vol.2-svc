package SVC.Services;

import SVC.DTOs.CreateRiskAssessmentRequest;
import SVC.Enums.RiskDecision;
import SVC.Enums.RiskLevel;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class RiskScoringService {

    private final int reviewThreshold;
    private final int blockThreshold;

    public RiskScoringService(
            @Value("${app.risk.threshold.review:40}") int reviewThreshold,
            @Value("${app.risk.threshold.block:70}") int blockThreshold
    ) {
        this.reviewThreshold = reviewThreshold;
        this.blockThreshold = blockThreshold;
    }

    public RiskScoringResult evaluate(CreateRiskAssessmentRequest request) {
        List<String> reasons = new ArrayList<>();
        int score = 0;

        if (!"ACTIVE".equalsIgnoreCase(request.getAccountStatus())) {
            reasons.add("Sender account is not active.");
            return new RiskScoringResult(100, RiskLevel.HIGH, RiskDecision.BLOCK, reasons);
        }

        if (isHighAmountVsBalance(request.getAmount(), request.getSenderBalance())) {
            score += 30;
            reasons.add("Transfer amount exceeds 80% of wallet balance.");
        }

        if (exceedsDailyLimit(request.getAmount(), request.getWithdrawnToday(), request.getDailyLimit())) {
            score += 40;
            reasons.add("Transfer would exceed the daily withdraw limit.");
        }

        if (request.getTransfersTodayCount() >= 3) {
            score += 20;
            reasons.add("Sender has already made 3 or more transfers today.");
        }

        if (isNightTransfer(request.getHourOfDay())) {
            score += 15;
            reasons.add("Transfer was initiated during night hours.");
        }

        if (request.isNewReceiver()) {
            score += 10;
            reasons.add("First transfer to this receiver.");
        }

        if (!request.isReceiverHasBankCard()) {
            score += 25;
            reasons.add("Receiver has no registered bank card.");
        }

        RiskLevel level = resolveLevel(score);
        RiskDecision decision = resolveDecision(score);

        if (reasons.isEmpty()) {
            reasons.add("No elevated risk indicators detected.");
        }

        return new RiskScoringResult(score, level, decision, reasons);
    }

    private boolean isHighAmountVsBalance(BigDecimal amount, BigDecimal balance) {

        if (balance == null || balance.signum() <= 0) {
            return true;
        }

        BigDecimal threshold = balance.multiply(new BigDecimal("0.80"));
        return amount.compareTo(threshold) > 0;
    }

    private boolean exceedsDailyLimit(BigDecimal amount, BigDecimal withdrawnToday, BigDecimal dailyLimit) {

        BigDecimal withdrawn = withdrawnToday != null ? withdrawnToday : BigDecimal.ZERO;
        BigDecimal limit = dailyLimit != null ? dailyLimit : BigDecimal.ZERO;
        return withdrawn.add(amount).compareTo(limit) > 0;
    }

    private boolean isNightTransfer(int hourOfDay) {
        return hourOfDay >= 23 || hourOfDay < 6;
    }

    private RiskLevel resolveLevel(int score) {

        if (score >= blockThreshold) {
            return RiskLevel.HIGH;
        }

        if (score >= reviewThreshold) {
            return RiskLevel.MEDIUM;
        }

        return RiskLevel.LOW;
    }

    private RiskDecision resolveDecision(int score) {

        if (score >= blockThreshold) {
            return RiskDecision.BLOCK;
        }

        if (score >= reviewThreshold) {
            return RiskDecision.REVIEW;
        }

        return RiskDecision.ALLOW;
    }

    @Getter
    public static class RiskScoringResult {

        private final int score;
        private final RiskLevel level;
        private final RiskDecision decision;
        private final List<String> reasons;

        public RiskScoringResult(int score, RiskLevel level, RiskDecision decision, List<String> reasons) {

            this.score = score;
            this.level = level;
            this.decision = decision;
            this.reasons = List.copyOf(reasons);
        }
    }
}
