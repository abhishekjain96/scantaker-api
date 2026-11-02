package com.scantaker.api.repository;

import com.scantaker.api.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByUserId(Long userId);

    List<DeviceToken> findByRole(String role);

    List<DeviceToken> findByCentreId(Long centreId);

    @Modifying
    @Query("DELETE FROM DeviceToken d WHERE d.token = :token")
    void deleteByToken(@Param("token") String token);

    @Modifying
    @Query("DELETE FROM DeviceToken d WHERE d.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
