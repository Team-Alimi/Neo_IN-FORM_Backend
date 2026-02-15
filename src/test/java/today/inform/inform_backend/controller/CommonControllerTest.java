package today.inform.inform_backend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import today.inform.inform_backend.dto.CategoryListResponse;
import today.inform.inform_backend.dto.VendorListResponse;
import today.inform.inform_backend.service.CommonService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class CommonControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private CommonService commonService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .build();
    }

    @Test
    @DisplayName("벤더 목록 조회 API를 호출한다.")
    void getVendors_Api_Test() throws Exception {
        // given
        VendorListResponse response = VendorListResponse.builder()
                .vendorId(1)
                .vendorName("컴공")
                .build();
        given(commonService.getVendors(any())).willReturn(List.of(response));

        // when & then
        mockMvc.perform(get("/api/v1/vendors")
                        .param("type", "SCHOOL")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].vendor_name").value("컴공"));
    }

    @Test
    @DisplayName("카테고리 목록 조회 API를 호출한다.")
    void getCategories_Api_Test() throws Exception {
        // given
        CategoryListResponse response = CategoryListResponse.builder()
                .categoryId(1)
                .categoryName("장학")
                .build();
        given(commonService.getCategories()).willReturn(List.of(response));

        // when & then
        mockMvc.perform(get("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].category_name").value("장학"));
    }
}
