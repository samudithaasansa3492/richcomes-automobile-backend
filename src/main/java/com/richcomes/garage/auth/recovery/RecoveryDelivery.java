package com.richcomes.garage.auth.recovery;
public interface RecoveryDelivery {
    boolean available();
    void send(String email, String purpose, String token);
}
