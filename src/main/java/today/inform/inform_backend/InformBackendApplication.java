package today.inform.inform_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class InformBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(InformBackendApplication.class, args);
	}

}
