package com.cactusds.backend.dto;

/** Login second step: send either the 6-digit code or one recovery code. */
public record Twofactorverifyrequest(String code, String recoveryCode) {}