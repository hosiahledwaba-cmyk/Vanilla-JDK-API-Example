// KycRequest.java
package com.robinhood.model;

import java.time.LocalDateTime;

public class KycRequest {
    private int id;
    private String email, fullName, documentType, documentUrl, status;
    private LocalDateTime submittedAt, processedAt;
    private String adminNote;
    // constructors + getters/setters
}
