package com.techindna.springbootjwttemplate.repository;

import com.techindna.springbootjwttemplate.entity.enums.HostStatus;
import com.techindna.springbootjwttemplate.repository.model.JHost;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface HostRepository extends JpaRepository<JHost, UUID> {

    Optional<JHost> findByIpAddressAndUser_Id(String ipAddress, UUID userId);

    @Query("""
           select h from JHost h
           where (:userId is null or h.user.id = :userId)
             and (:ipAddress is null or lower(h.ipAddress) like concat('%', :ipAddress, '%'))
             and (:status is null or h.status = :status)
           """)
    Page<JHost> search(
            @Param("userId") UUID userId,
            @Param("ipAddress") String ipAddress,
            @Param("status") HostStatus status,
            Pageable pageable);
}
