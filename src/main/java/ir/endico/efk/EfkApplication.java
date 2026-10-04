package ir.endico.efk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
public class EfkApplication {

    public static void main(String[] args) {
        SpringApplication.run(EfkApplication.class, args);
    }
}
