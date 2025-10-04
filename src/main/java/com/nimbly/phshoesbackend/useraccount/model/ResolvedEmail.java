package com.nimbly.phshoesbackend.useraccount.model;

public record ResolvedEmail(String verificationId,
                            String userId,
                            String email) {
}
