package com.cactusds.backend.dto;

/** Disabling 2FA needs the account password (when there is one) plus a code or a recovery code. */
public record Twofactordisablerequest(String password, String code, String recoveryCode) {}