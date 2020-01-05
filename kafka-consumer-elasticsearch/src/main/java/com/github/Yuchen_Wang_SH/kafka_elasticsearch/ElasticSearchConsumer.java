package com.github.Yuchen_Wang_SH.kafka_elasticsearch;

import com.google.gson.JsonParser;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.analysis.PreConfiguredCharFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;

public class ElasticSearchConsumer {
    private static Logger logger = LoggerFactory.getLogger(ElasticSearchConsumer.class.getName());
    private static JsonParser jsonParser = new JsonParser();

    private static KafkaConsumer<String, String> createConsumer(String topic) {
        String bootstrapServer = "localhost:9092";
        String groupId = "kafka-elasticsearch";

        Properties properties = new Properties();
        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"); // Disable auto commit offset
        properties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100");

        KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(properties);
        consumer.subscribe(Arrays.asList(topic));
        return consumer;
    }

    private static RestHighLevelClient createClient() {
        String hostname = "kafka-9749272695.us-east-1.bonsaisearch.net";
        String username = "5waq0c4d1k";
        String password = "d8ffv6cws6";

        // Since we are using a cloud service, we need this credential.
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));

        RestClientBuilder builder = RestClient.builder(
                new HttpHost(hostname, 443, "https"))
                .setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
                    @Override
                    public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpAsyncClientBuilder) {
                        return httpAsyncClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    }
                });

        RestHighLevelClient client = new RestHighLevelClient(builder);
        return client;
    }

    private static String extractIdFromTweet(String tweetJson) {
        // gson library
        return jsonParser.parse(tweetJson).getAsJsonObject().get("id_str").getAsString();
    }

    public static void main(String[] args) throws IOException {
        RestHighLevelClient client = createClient();

        KafkaConsumer<String, String> consumer = createConsumer("twitter_tweets");
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            int recordCount = records.count();
            logger.info("Received " + recordCount + " records");

            BulkRequest bulkRequest = new BulkRequest();

            for (ConsumerRecord<String, String> record: records) {
                // To make consumer idempotent, we can generate an id for each message.
                // Two strategies:
                // 1. Kafka generic ID
//                String id = record.topic() + "_" + record.partition() + "_" + record.offset();
                // 2. Twitter specific ID
                try {
                    String id = extractIdFromTweet(record.value());
                    IndexRequest indexRequest = new IndexRequest("twitter", "tweets", id)
                            .source(record.value(), XContentType.JSON);

                    bulkRequest.add(indexRequest); // Add to bulkrequest
                } catch (NullPointerException e) {
                    logger.warn("skipping bad data: " + record.value());
                }
            }
            if (recordCount > 0) {
                BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
                logger.info("Committing the offsets...");
                consumer.commitSync();
                logger.info("Offsets have been committed.");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

//        client.close();
    }
}
