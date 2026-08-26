package com.techindna.springbootjwttemplate.repository;

import com.techindna.springbootjwttemplate.repository.model.JHost;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HostRepository extends JpaRepository<JHost, UUID> {

    Optional<JHost> findByIpAddressAndUser_Id(String ipAddress, UUID userId);

    Optional<JHost> findByIpAddress(String ipAddress);
}
