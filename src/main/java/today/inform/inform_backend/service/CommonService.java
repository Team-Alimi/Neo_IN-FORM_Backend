package today.inform.inform_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import today.inform.inform_backend.dto.CategoryListResponse;
import today.inform.inform_backend.dto.VendorListResponse;
import today.inform.inform_backend.entity.VendorType;
import today.inform.inform_backend.repository.CategoryRepository;
import today.inform.inform_backend.repository.VendorRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommonService {

    private final VendorRepository vendorRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<VendorListResponse> getVendors(VendorType type) {
        List<today.inform.inform_backend.entity.Vendor> vendors;
        if (type == null) {
            vendors = vendorRepository.findAll();
        } else {
            vendors = vendorRepository.findAllByVendorType(type);
        }

        return vendors.stream()
                .map(v -> VendorListResponse.builder()
                        .vendorId(v.getVendorId())
                        .vendorName(v.getVendorName())
                        .vendorInitial(v.getVendorInitial())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CategoryListResponse> getCategories() {
        return categoryRepository.findAll().stream()
                .map(c -> CategoryListResponse.builder()
                        .categoryId(c.getCategoryId())
                        .categoryName(c.getCategoryName())
                        .build())
                .collect(Collectors.toList());
    }
}
