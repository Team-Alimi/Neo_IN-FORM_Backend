package today.inform.inform_backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import today.inform.inform_backend.entity.Vendor;
import today.inform.inform_backend.entity.VendorType;
import today.inform.inform_backend.repository.VendorRepository;

@Configuration
@Profile("!test") // 테스트 환경에서는 실행되지 않도록 설정
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final VendorRepository vendorRepository;

    @Override
    public void run(String... args) {
        if (vendorRepository.count() == 0) {
            // 인하대학교 기본 학과 샘플 데이터
            vendorRepository.save(Vendor.builder()
                    .vendorName("컴퓨터공학과")
                    .vendorInitial("CSE")
                    .vendorType(VendorType.SCHOOL)
                    .build());
            
            vendorRepository.save(Vendor.builder()
                    .vendorName("정보통신공학과")
                    .vendorInitial("ICE")
                    .vendorType(VendorType.SCHOOL)
                    .build());

            System.out.println("✅ 초기 학과 데이터가 생성되었습니다.");
        }
    }
}
