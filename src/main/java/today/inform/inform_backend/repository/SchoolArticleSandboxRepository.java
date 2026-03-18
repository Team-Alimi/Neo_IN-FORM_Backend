package today.inform.inform_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import today.inform.inform_backend.entity.AdminStatus;
import today.inform.inform_backend.entity.SchoolArticleSandbox;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface SchoolArticleSandboxRepository extends JpaRepository<SchoolArticleSandbox, Integer> {
    Page<SchoolArticleSandbox> findAllByAdminStatusOrderByCreatedAtAsc(AdminStatus adminStatus, Pageable pageable);
    List<SchoolArticleSandbox> findAllByAdminStatusOrderByCreatedAtAsc(AdminStatus adminStatus);
    long countByAdminStatus(AdminStatus adminStatus);
}
