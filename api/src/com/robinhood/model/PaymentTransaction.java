package com.robinhood.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class PaymentTransaction {
    private int id;
    private String email;
    private String type = "deposit";
    private String provider;
    private String gateway;
    private String providerTxId;
    private int amount;
    private String currency = "ZAR";
    private String reference;
    private String returnUrl;
    private String notifyUrl;
    private String appleToken;
    private String voucherCode;
    private String status = "pending";
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;
    private Map<String, Object> metadata = new HashMap<>();

    public PaymentTransaction() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getProvider() { return provider != null ? provider : gateway; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getGateway() { return gateway != null ? gateway : provider; }
    public void setGateway(String gateway) {
        this.gateway = gateway;
        if (this.provider == null) this.provider = gateway;
    }

    public String getProviderTxId() { return providerTxId; }
    public void setProviderTxId(String providerTxId) { this.providerTxId = providerTxId; }

    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }

    public String getNotifyUrl() { return notifyUrl; }
    public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }

    public String getAppleToken() { return appleToken; }
    public void setAppleToken(String appleToken) { this.appleToken = appleToken; }

    public String getVoucherCode() { return voucherCode; }
    public void setVoucherCode(String voucherCode) { this.voucherCode = voucherCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Map<String, Object> getMetadata() { return metadata; }
    @SuppressWarnings("unchecked")
    public void setMetadata(Map<?, ?> metadata) {
        this.metadata = new HashMap<>();
        if (metadata != null) {
            for (Map.Entry<?, ?> e : metadata.entrySet()) {
                this.metadata.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
    }
}
