package com.aqualink.servicepoint;

import com.aqualink.community.Community;
import com.aqualink.order.Arrangement;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "service_points")
@Getter
@NoArgsConstructor
public class ServicePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Arrangement arrangement;
}