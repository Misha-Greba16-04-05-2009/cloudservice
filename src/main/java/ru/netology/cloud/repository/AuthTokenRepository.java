package ru.netology.cloud.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.netology.cloud.entity.AuthToken;

public interface AuthTokenRepository extends JpaRepository<AuthToken, String> {
}
