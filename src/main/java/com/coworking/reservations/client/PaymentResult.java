package com.coworking.reservations.client;

public record PaymentResult(Status status, String transactionId) {

    public enum Status {
        APPROVED,
        UNAVAILABLE
    }

    public static PaymentResult approved(String transactionId) {
        return new PaymentResult(Status.APPROVED, transactionId);
    }

    public static PaymentResult unavailable() {
        return new PaymentResult(Status.UNAVAILABLE, null);
    }

    public boolean isApproved() {
        return status == Status.APPROVED;
    }
}
