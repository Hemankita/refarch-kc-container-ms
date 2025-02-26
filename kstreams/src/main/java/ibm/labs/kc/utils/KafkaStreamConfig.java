package ibm.labs.kc.utils;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsConfig;

/**
 *
 */
public class KafkaStreamConfig {

    public static final String ORDERS_TOPIC = "orders";
    public static final String REJECTED_ORDERS_TOPIC = "rejected-orders";
    public static final String ALLOCATED_ORDERS_TOPIC = "allocated-orders";
    public static final String CONTAINERS_TOPIC = "containers";
	public static final String CONTAINERS_STORE_NAME = "queryable-container-store";
	
    public static final String ERROR_TOPIC = "errors";
    public static final String ICP_ENV = "ICP";
    public static final String IC_ENV = "IBMCLOUD";
    public static final long PRODUCER_TIMEOUT_SECS = 10;
    public static final long PRODUCER_CLOSE_TIMEOUT_SEC = 10;
    public static final String CONSUMER_GROUP_ID = "order-command-grp";
    public static final Duration CONSUMER_POLL_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration CONSUMER_CLOSE_TIMEOUT = Duration.ofSeconds(10);
    public static final long TERMINATION_TIMEOUT_SEC = 10;

    

    
    /**
     * Take into account the environment variables if set
     * 
     * @return common kafka properties
     */
    private static Properties buildCommonProperties() {
        Properties properties = new Properties();
        Map<String, String> env = System.getenv();

        if (IC_ENV.equals(env.get("KAFKA_ENV")) || ICP_ENV.equals(env.get("KAFKA_ENV"))) {
            if (env.get("KAFKA_BROKERS") == null) {
                throw new IllegalStateException("Missing environment variable KAFKA_BROKERS");
            }
            if (env.get("KAFKA_APIKEY") == null) {
                throw new IllegalStateException("Missing environment variable KAFKA_APIKEY");
            }
            properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, env.get("KAFKA_BROKERS"));
            properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
            properties.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
            properties.put(SaslConfigs.SASL_JAAS_CONFIG,
                    "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"token\" password=\""
                            + env.get("KAFKA_APIKEY") + "\";");
            properties.put(SslConfigs.SSL_PROTOCOL_CONFIG, "TLSv1.2");
            if (env.get("JKS_LOCATION") != null) {
            	 properties.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, env.get("JKS_LOCATION"));
            }
            if (env.get("TRUSTSTORE_PWD") != null) {
            	properties.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, env.get("TRUSTSTORE_PWD"));
            }
            
            properties.put(SslConfigs.SSL_ENABLED_PROTOCOLS_CONFIG, "TLSv1.2");
            properties.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "HTTPS");
        } else {
            if (env.get("KAFKA_BROKERS") == null) {
                properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
            } else {
                properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, env.get("KAFKA_BROKERS"));
            }
        }

        return properties;
    }

    // Kafka Streams requires at least the APPLICATION_ID_CONFIG and BOOTSTRAP_SERVERS_CONFIG
	public static Properties getStreamsProperties(String appID) {
	    Properties properties = buildCommonProperties();
	    properties.put(StreamsConfig.APPLICATION_ID_CONFIG, appID);
	    properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
	    properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
	    properties.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, 3);
		return properties;
	}

    public static Properties getProducerProperties(String clientId) {
        Properties properties = buildCommonProperties();
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
        properties.put(ProducerConfig.ACKS_CONFIG, "1");
        return properties;
    }

}
