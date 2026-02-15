package today.inform.inform_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import today.inform.inform_backend.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
}
