package today.inform.inform_backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User extends BaseCreatedTimeEntity { // createdAt만 상속

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer userId; // int에 맞춤

    @Column(nullable = false, unique = true)
    private String email;

    @Column(length = 50)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "major_id")
    private Vendor major;

    public void updateName(String name) {
        this.name = name;
    }

    public static void validateInhaDomain(String email) {
        if (email == null || (!email.endsWith("@inha.edu") && !email.endsWith("@inha.ac.kr"))) {
            throw new IllegalArgumentException("학교 이메일(@inha.edu/@inha.ac.kr)로만 로그인할 수 있습니다.");
        }
    }
}