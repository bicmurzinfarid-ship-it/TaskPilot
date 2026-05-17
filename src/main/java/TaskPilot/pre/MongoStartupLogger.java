package TaskPilot.pre;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class MongoStartupLogger {

    private static final Logger log = LoggerFactory.getLogger(MongoStartupLogger.class);
    private final Environment env;

    public MongoStartupLogger(Environment env) {
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logMongoConfig() {
        log.info("=== MONGO DIAGNOSTICS ===");
        log.info("spring.data.mongodb.uri    = {}", env.getProperty("spring.data.mongodb.uri"));
        log.info("spring.data.mongodb.host   = {}", env.getProperty("spring.data.mongodb.host"));
        log.info("spring.data.mongodb.port   = {}", env.getProperty("spring.data.mongodb.port"));
        log.info("spring.data.mongodb.database = {}", env.getProperty("spring.data.mongodb.database"));
        log.info("MONGOHOST env              = {}", System.getenv("MONGOHOST"));
        log.info("MONGOPORT env              = {}", System.getenv("MONGOPORT"));
        log.info("MONGODB_URI env            = {}", System.getenv("MONGODB_URI"));
        log.info("active profiles            = {}", String.join(",", env.getActiveProfiles()));
        log.info("=========================");
    }
}
