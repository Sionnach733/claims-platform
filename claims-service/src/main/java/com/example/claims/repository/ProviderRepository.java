package com.example.claims.repository;

import com.example.claims.domain.Provider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    Optional<Provider> findByNpi(String npi);
}
