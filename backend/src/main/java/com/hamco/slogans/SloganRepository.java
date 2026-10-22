package com.hamco.slogans;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** JPA supplies CRUD SQL; explicit sort is part of the API contract. */
interface SloganRepository extends JpaRepository<Slogan, Integer> {
    List<Slogan> findAllByOrderByCreatedAtDesc();

    // Project only public text; admin metadata never needs to leave the database on a site read.
    @Query("select s.text from Slogan s where s.active = true")
    List<String> findActiveTexts();
}
