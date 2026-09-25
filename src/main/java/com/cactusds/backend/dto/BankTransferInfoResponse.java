package com.cactusds.backend.dto;

public record BankTransferInfoResponse(boolean configured, String bankName, String rib, String iban, String holder) {
}
