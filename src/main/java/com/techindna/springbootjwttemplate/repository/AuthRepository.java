package com.techindna.springbootjwttemplate.repository;

import com.techindna.springbootjwttemplate.repository.model.JUser;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthRepository extends JpaRepository<JUser, UUID> { }
