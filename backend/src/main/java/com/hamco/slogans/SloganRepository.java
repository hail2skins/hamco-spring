package com.hamco.slogans;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** JPA supplies CRUD SQL; explicit sort is part of the API contract. */
interface SloganRepository extends JpaRepository<Slogan, Integer> {
    List<Slogan> findAllByOrderByCreatedAtDesc();
}
