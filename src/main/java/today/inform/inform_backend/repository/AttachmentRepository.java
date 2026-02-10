package today.inform.inform_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import today.inform.inform_backend.entity.Attachment;
import today.inform.inform_backend.entity.VendorType;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Integer> {
    List<Attachment> findAllByArticleIdAndArticleType(Integer articleId, VendorType articleType);
}
