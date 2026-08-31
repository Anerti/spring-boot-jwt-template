package com.techindna.springbootjwttemplate.repository.model;

import com.techindna.springbootjwttemplate.entity.enums.EventLogStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private JHost host;

    @Column(name = "user_agent", nullable = false, length = 512)
    private String userAgent;

    @Column(name = "description", length = 100)
    private String description;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private EventLogStatus status = EventLogStatus.INFO;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
