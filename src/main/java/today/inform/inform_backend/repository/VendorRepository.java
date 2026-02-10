package today.inform.inform_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import today.inform.inform_backend.entity.Vendor;

public interface VendorRepository extends JpaRepository<Vendor, Integer> {
}
