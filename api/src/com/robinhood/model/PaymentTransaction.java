// PaymentTransaction.java
package com.robinhood.model;

import java.time.LocalDateTime;
import java.util.Map;

public class PaymentTransaction {
    private int id;
    private String email, type, provider, providerTxId, status;
    private int amount;
    private String currency;
    private LocalDateTime createdAt, updatedAt;
    private Map<String,Object> metadata;
	private String gateway;
    // constructors + getters/setters
}
