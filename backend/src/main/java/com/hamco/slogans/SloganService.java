package com.hamco.slogans;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactions surround each mutation; the controller controls HTTP errors. */
@Service
@Transactional
public class SloganService {
    private final SloganRepository slogans;
    private final Clock clock = Clock.systemUTC();

    public SloganService(SloganRepository slogans) {
        this.slogans = slogans;
    }

    @Transactional(readOnly = true)
    public List<SloganResponse> list() {
        // Admins see inactive entries too, sorted newest first as in ASP.NET.
        return slogans.findAllByOrderByCreatedAtDesc().stream()
                .map(SloganResponse::from).toList();
    }

    public SloganResponse create(SloganRequest request, String userId) {
        Slogan slogan = new Slogan(request.text().trim(),
                request.isActive() == null || request.isActive(), userId, clock.instant());
        return SloganResponse.from(slogans.save(slogan));
    }

    public Optional<SloganResponse> update(Integer id, SloganRequest request) {
        return slogans.findById(id).map(slogan -> {
            slogan.update(request.text() == null ? null : request.text().trim(),
                    request.isActive(), clock.instant());
            // JPA dirty checking writes the mutation on transaction commit.
            return SloganResponse.from(slogan);
        });
    }

    public boolean delete(Integer id) {
        Optional<Slogan> found = slogans.findById(id);
        if (found.isEmpty()) return false;
        slogans.delete(found.get());
        return true;
    }
}
