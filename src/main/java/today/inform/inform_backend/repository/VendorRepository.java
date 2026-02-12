package today.inform.inform_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import today.inform.inform_backend.entity.Vendor;
import today.inform.inform_backend.entity.VendorType;

import java.util.List;

public interface VendorRepository extends JpaRepository<Vendor, Integer> {
    List<Vendor> findAllByVendorType(VendorType vendorType);
}
