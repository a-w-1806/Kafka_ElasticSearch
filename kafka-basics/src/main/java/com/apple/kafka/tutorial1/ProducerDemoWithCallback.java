package com.apple.kafka.tutorial1;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class ProducerDemoWithCallback {
    public static void main(String[] args) {
        final Logger logger = LoggerFactory.getLogger(ProducerDemoWithCallback.class);

        String bootstrapServer = "localhost:9092";
        Properties properties = new Properties();

        // Create producer properties
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // Create the Kafka producer
        KafkaProducer<String, String> producer = new KafkaProducer<String, String>(properties);

        // Create a producer record
        ProducerRecord<String, String> record =
                new ProducerRecord<String, String>("first_topic", "Hello, world");

        // Send data - asynchronous
        producer.send(record, new Callback() {
            public void onCompletion(RecordMetadata recordMetadata, Exception e) {
                // Execute every time
                if (e == null) {
                    // Success
                    logger.info("Received new metadata. \n" + "Topic: " + recordMetadata.topic() +
                            "\n");
                } else {
                    logger.error("Error while producing", e);
                }
            }
        });

        // Flush
        producer.flush();

        // Flush and close
        producer.close();
    }
}
