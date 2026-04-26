package today.inform.inform_backend.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GcsConfig {

    @Value("${gcs.project-id}")
    private String projectId;

    /**
     * GCP VM 환경: 인스턴스 서비스 계정으로 자동 인증
     * 로컬 개발: GOOGLE_APPLICATION_CREDENTIALS 환경변수 필요
     */
    @Bean
    public Storage storage() {
        return StorageOptions.newBuilder()
                .setProjectId(projectId)
                .build()
                .getService();
    }
}
