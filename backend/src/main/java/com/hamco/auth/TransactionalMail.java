package com.hamco.auth;

/** Isolate side effects from account transactions; implementations never expose tokens in logs. */
public interface TransactionalMail {
    void send(String email, String subject, String link);
}
