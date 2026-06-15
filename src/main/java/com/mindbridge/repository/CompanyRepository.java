package com.mindbridge.repository;

import com.mindbridge.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID> {
    boolean existsByName(String name);
    Optional<Company> findByInviteCode(String inviteCode);   // ← used during employee registration
}