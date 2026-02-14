package today.inform.inform_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

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

    @Builder.Default
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Notification> notifications = new ArrayList<>();

    public void updateName(String name) {
        this.name = name;
    }

    public void updateMajor(Vendor major) {
        this.major = major;
    }

        public static void validateInhaDomain(String email) {

            if (email == null || (!email.endsWith("@inha.edu") && !email.endsWith("@inha.ac.kr"))) {

                throw new today.inform.inform_backend.common.exception.BusinessException(today.inform.inform_backend.common.exception.ErrorCode.DOMAIN_RESTRICTED);

            }

        }

    }

    