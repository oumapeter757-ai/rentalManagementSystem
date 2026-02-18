package com.peterscode.rentalmanagementsystem.service.payment;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import com.stripe.param.ChargeCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class StripeService {

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
        log.info("Stripe API initialized");
    }

    /**
     * Charges a card using a token (for simple charges)
     */
    public String charge(String token, BigDecimal amount, String currency, String description) throws StripeException {
        // Stripe expects amount in cents/smallest currency unit
        long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();

        ChargeCreateParams params = ChargeCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(currency)
                .setDescription(description)
                .setSource(token)
                .build();

        Charge charge = Charge.create(params);
        return charge.getId();
    }

    /**
     * Creates a PaymentIntent (for more complex flows/3D Secure)
     * This is generally preferred for SCA compliance, but for this specific request 
     * using a simple Card charge might be what's expected for "add card integration".
     * However, PaymentIntent is the modern standard.
     * We'll stick to a simple charge if a token is provided, or support PaymentIntent if needed.
     */
}
