package today.inform.inform_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import today.inform.inform_backend.entity.SchoolArticleVendorSandbox;

import java.util.List;

public interface SchoolArticleVendorSandboxRepository extends JpaRepository<SchoolArticleVendorSandbox, Integer> {
    List<SchoolArticleVendorSandbox> findAllBySandboxArticleSandboxId(Integer sandboxId);
    void deleteAllBySandboxArticleSandboxId(Integer sandboxId);
}
