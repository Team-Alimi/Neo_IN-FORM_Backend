package today.inform.inform_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import today.inform.inform_backend.entity.AdminStatus;
import today.inform.inform_backend.entity.SchoolArticleSandbox;

import java.util.List;

public interface SchoolArticleSandboxRepository extends JpaRepository<SchoolArticleSandbox, Integer> {
    List<SchoolArticleSandbox> findAllByAdminStatusOrderByCreatedAtAsc(AdminStatus adminStatus);
    long countByAdminStatus(AdminStatus adminStatus);
}
