package com.peterscode.rentalmanagementsystem.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MpesaTransactionStatusResponse {

    @JsonProperty("MerchantRequestID")
    private String merchantRequestID;

    @JsonProperty("CheckoutRequestID")
    private String checkoutRequestID;

    @JsonProperty("ResponseCode")
    private String responseCode;

    @JsonProperty("ResponseDescription")
    private String responseDescription;

    @JsonProperty("ResultCode")
    private String resultCode;

    @JsonProperty("ResultDesc")
    private String resultDesc;

    @JsonProperty("Amount")
    private String amount;

    @JsonProperty("MpesaReceiptNumber")
    private String mpesaReceiptNumber;

    @JsonProperty("Balance")
    private String balance;

    @JsonProperty("TransactionDate")
    private String transactionDate;

    @JsonProperty("PhoneNumber")
    private String phoneNumber;

    // Helper method to check if transaction was successful
    public boolean isSuccessful() {
        return "0".equals(resultCode);
    }

    // Helper method to check if transaction failed
    public boolean isFailed() {
        return resultCode != null && !"0".equals(resultCode);
    }

    // Helper method to check if transaction was cancelled
    public boolean isCancelled() {
        return "1032".equals(resultCode);
    }

    // Helper method to check if user didn't enter PIN
    public boolean isPinNotEntered() {
        return "17".equals(resultCode);
    }

    // Helper method to check if insufficient balance
    public boolean isInsufficientBalance() {
        return "1".equals(resultCode);
    }
}