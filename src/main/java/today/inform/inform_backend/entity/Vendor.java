package today.inform.inform_backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "vendors")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Vendor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer vendorId; // 설계서 int에 맞춰 Integer로 조정

    @Column(nullable = false, length = 100)
    private String vendorName;

    @Column(nullable = false, unique = true, length = 100)
    private String vendorInitial;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private VendorType vendorType = VendorType.SCHOOL; // 기본값 설정
}