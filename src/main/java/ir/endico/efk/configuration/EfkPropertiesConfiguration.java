package ir.endico.efk.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Getter
@Setter
@ConditionalOnProperty(
        prefix = "efk",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class EfkPropertiesConfiguration {

    private boolean enabled = true;

    private ElasticsearchProperties elasticsearch = new ElasticsearchProperties();

    @Getter
    @Setter
    public static class ElasticsearchProperties {

        private String host = "localhost";

        private int port = 9200;

        private int connectionTimeout = 3000;

        private int socketTimeout = 30000;

    }

}
