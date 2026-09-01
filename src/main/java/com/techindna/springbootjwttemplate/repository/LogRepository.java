package com.techindna.springbootjwttemplate.repository;

import com.techindna.springbootjwttemplate.entity.enums.EventLogStatus;
import com.techindna.springbootjwttemplate.repository.model.JEventLog;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LogRepository extends JpaRepository<JEventLog, UUID> {

    @Query("""
           select e from JEventLog e
           where e.host.user.id = :userId
             and (:status is null or e.status = :status)
             and (:startDate is null or e.createdAt >= :startDate)
             and (:endDate is null or e.createdAt < :endDate)
           """)
    Page<JEventLog> search(
            @Param("userId") UUID userId,
            @Param("status") EventLogStatus status,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable);
}
