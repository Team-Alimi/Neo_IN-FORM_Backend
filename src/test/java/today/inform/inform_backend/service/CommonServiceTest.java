package today.inform.inform_backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import today.inform.inform_backend.dto.CategoryListResponse;
import today.inform.inform_backend.dto.VendorListResponse;
import today.inform.inform_backend.entity.Category;
import today.inform.inform_backend.entity.Vendor;
import today.inform.inform_backend.entity.VendorType;
import today.inform.inform_backend.repository.CategoryRepository;
import today.inform.inform_backend.repository.VendorRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CommonServiceTest {

    @InjectMocks
    private CommonService commonService;

    @Mock
    private VendorRepository vendorRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("특정 타입의 벤더 목록을 조회한다.")
    void getVendors_WithType_Success() {
        // given
        Vendor vendor = Vendor.builder().vendorId(1).vendorName("학과").vendorType(VendorType.SCHOOL).build();
        given(vendorRepository.findAllByVendorType(VendorType.SCHOOL)).willReturn(List.of(vendor));

        // when
        List<VendorListResponse> result = commonService.getVendors(VendorType.SCHOOL);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVendorName()).isEqualTo("학과");
    }

    @Test
    @DisplayName("타입이 null이면 전체 벤더 목록을 조회한다.")
    void getVendors_WithNullType_Success() {
        // given
        Vendor v1 = Vendor.builder().vendorId(1).vendorName("학과").vendorType(VendorType.SCHOOL).build();
        Vendor v2 = Vendor.builder().vendorId(2).vendorName("동아리").vendorType(VendorType.CLUB).build();
        given(vendorRepository.findAll()).willReturn(List.of(v1, v2));

        // when
        List<VendorListResponse> result = commonService.getVendors(null);

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("전체 카테고리 목록을 조회한다.")
    void getCategories_Success() {
        // given
        Category category = Category.builder().categoryId(1).categoryName("장학").build();
        given(categoryRepository.findAll()).willReturn(List.of(category));

        // when
        List<CategoryListResponse> result = commonService.getCategories();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategoryName()).isEqualTo("장학");
    }
}
