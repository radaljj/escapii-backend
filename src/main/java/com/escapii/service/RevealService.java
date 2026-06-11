package com.escapii.service;

import java.util.Map;

public interface RevealService {
    Map<String, Object> getRevealInfo(String token);

    /** Beleži trenutak kad je korisnik ogrebaо scratch karticu. Idempotentno - samo prvi put postavlja timestamp. */
    void confirmRevealed(String token);
}
