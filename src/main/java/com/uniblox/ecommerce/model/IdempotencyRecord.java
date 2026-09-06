package com.uniblox.ecommerce.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord {
    @Id
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String key;

    @Column(nullable = false)
    private Long orderId;

    @Lob
    @Column(nullable = false, columnDefinition="TEXT")
    private String responseBody;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
