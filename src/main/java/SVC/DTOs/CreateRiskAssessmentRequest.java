package SVC.DTOs;

import SVC.Enums.AssessmentStatus;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CreateRiskAssessmentRequest {

    private UUID transactionRef;

    @NotBlank(message = "Sender username is required.")
    @Size(max = 30)
    private String senderUsername;

    @NotBlank(message = "Receiver username is required.")
    @Size(max = 30)
    private String receiverUsername;

    @NotNull(message = "Amount is required.")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero.")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal amount;

    @NotNull(message = "Sender balance is required.")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal senderBalance;

    @NotNull(message = "Withdrawn today amount is required.")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal withdrawnToday;

    @NotNull(message = "Daily limit is required.")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal dailyLimit;

    @Min(0)
    private int transfersTodayCount;

    private boolean receiverHasBankCard;

    private boolean newReceiver;

    @NotBlank(message = "Account status is required.")
    private String accountStatus;

    @Min(0)
    @Max(23)
    private int hourOfDay;
}
