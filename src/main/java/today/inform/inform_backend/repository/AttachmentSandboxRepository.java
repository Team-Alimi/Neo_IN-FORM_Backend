package today.inform.inform_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import today.inform.inform_backend.entity.AttachmentSandbox;

import java.util.List;

public interface AttachmentSandboxRepository extends JpaRepository<AttachmentSandbox, Integer> {
    List<AttachmentSandbox> findAllBySandboxArticleSandboxId(Integer sandboxId);
    void deleteAllBySandboxArticleSandboxId(Integer sandboxId);
}
