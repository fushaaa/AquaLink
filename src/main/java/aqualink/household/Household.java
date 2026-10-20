package aqualink.household;

import com.aqualink.community.Community;
import com.aqualink.servicepoint.ServicePoint;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import src.main.java.aqualink.community.GeneratedValue;

import java.math.BigDecimal;

@Entity
@Table(name = "households")
@Getter
@NoArgsConstructor
public class Household {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_point_id", nullable = false)
    private ServicePoint servicePoint;

    @Column(precision = 9, scale = 6, nullable = false)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6, nullable = false)
    private BigDecimal longitude;

    @Column(nullable = false)
    private boolean vulnerable;
    
    @Column(name = "public_id", nullable = false, unique = true,
        updatable = false, length = 36)
private String publicId;
}