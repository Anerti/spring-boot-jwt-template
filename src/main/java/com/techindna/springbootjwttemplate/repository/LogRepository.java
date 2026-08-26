package com.techindna.springbootjwttemplate.repository;

import com.techindna.springbootjwttemplate.repository.model.JEventLog;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LogRepository extends JpaRepository<JEventLog, UUID> {

    Optional<JEventLog> findByHostId(UUID hostId);
}
