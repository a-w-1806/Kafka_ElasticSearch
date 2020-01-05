package com.github.Yuchen_Wang_SH.Kafka;

import com.google.common.collect.Lists;
import com.twitter.hbc.ClientBuilder;
import com.twitter.hbc.core.Client;
import com.twitter.hbc.core.Constants;
import com.twitter.hbc.core.Hosts;
import com.twitter.hbc.core.HttpHosts;
import com.twitter.hbc.core.endpoint.StatusesFilterEndpoint;
import com.twitter.hbc.core.processor.StringDelimitedProcessor;
import com.twitter.hbc.httpclient.auth.Authentication;
import com.twitter.hbc.httpclient.auth.OAuth1;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TwitterProducer {
    private String consumerKey = "ueKDPVbPjJoW7edkHqFAP9G7T";
    private String consumerSecret = "zabdtQ6OGD73pUaMNRUp6qD1Oufg13L43jgXWz5Ur4M2YQS9hd";
    private String accessToken = "1211396349821767681-Vk76rF9vdPkVgAwTBziuO91y3Wl2sn";
    private String accessSecret = "m4awnOl6J9kt8cHm8rBcuej9MRBAwM6wNPoo6IzfnH9w1";

    private Logger logger = LoggerFactory.getLogger(TwitterProducer.class.getName());

    private List<String> terms = Lists.newArrayList("bitcoin", "kafka", "java", "China", "sport");

    private void run() {
        logger.info("Setting up");
        // Create a Twitter client
        /** Set up your blocking queues: Be sure to size these properly based on expected TPS of your stream */
        BlockingQueue<String> msgQueue = new LinkedBlockingQueue<String>(1000);
        Client client = createTwitterClient(msgQueue);
        // Attempts to establish a connection.
        client.connect();

        // Create a Kafka producer
        KafkaProducer<String, String> producer = createKafkaProducer();

        // Add a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down the application...");
            logger.info("Shutting down the Twitter client...");
            client.stop();
            logger.info("Shutting down the Kafka producer...");
            producer.close();
            logger.info("Successfully closed!");
        }));

        // Loop and send to Kafka

        // on a different thread, or multiple different threads....
        while (!client.isDone()) {
            String msg = null;
            try {
                msg = msgQueue.poll(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                client.stop();
            }
            if (msg != null) {
                logger.info(msg);
                producer.send(new ProducerRecord<>("twitter_tweets", null, msg), new Callback() {
                    @Override
                    public void onCompletion(RecordMetadata recordMetadata, Exception e) {
                        if (e != null) {
                            logger.error("Something wrong has happened", e);
                        }
                    }
                });
            }
        }
        logger.info("End of Application");
    }

    private Client createTwitterClient(BlockingQueue<String> msgQueue) {
        /** Declare the host you want to connect to, the endpoint, and authentication (basic auth or oauth) */
        Hosts hosebirdHosts = new HttpHosts(Constants.STREAM_HOST);
        StatusesFilterEndpoint hosebirdEndpoint = new StatusesFilterEndpoint();

        hosebirdEndpoint.trackTerms(terms);

        // These secrets should be read from a config file
        Authentication hosebirdAuth = new OAuth1(consumerKey, consumerSecret, accessToken, accessSecret);

        // Creating a client
        ClientBuilder builder = new ClientBuilder()
                .name("Hosebird-Client-01")                              // optional: mainly for the logs
                .hosts(hosebirdHosts)
                .authentication(hosebirdAuth)
                .endpoint(hosebirdEndpoint)
                .processor(new StringDelimitedProcessor(msgQueue));

        Client hosebirdClient = builder.build();
        return hosebirdClient;
    }

    private KafkaProducer<String, String> createKafkaProducer() {
        String bootstrapServer = "localhost:9092";
        Properties properties = new Properties();

        // Create producer properties
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // Make safer
        properties.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        // Below are set automatically by enable_idempotence, but write down for clearance
        properties.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        properties.setProperty(ProducerConfig.RETRIES_CONFIG, Integer.toString(Integer.MAX_VALUE));
        // Kafka >= 1.1, or you should use 1.
        properties.setProperty(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");

        // High throughput producer (at the expense of a bit of latency and CPU usage)
        properties.setProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        properties.setProperty(ProducerConfig.LINGER_MS_CONFIG, "20");
        properties.setProperty(ProducerConfig.BATCH_SIZE_CONFIG, Integer.toString(32 * 1024)); // 32 KB

        // Create the Kafka producer
        KafkaProducer<String, String> producer = new KafkaProducer<String, String>(properties);

        return producer;
    }

    public static void main(String[] args) {
        new TwitterProducer().run();
    }
}
