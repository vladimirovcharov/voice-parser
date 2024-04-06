package org.example.voiceparser;

import org.apache.kafka.streams.KafkaStreams;
import org.example.voiceparser.config.StreamsConfiguration;
import org.example.voiceparser.services.MockSttClient;
import org.example.voiceparser.services.MockTranslateClient;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class VoiceParserApplication {

    public static void main(String[] args) {
        var streamsConfiguration = new StreamsConfiguration();
        var voiceParserTopology = new VoiceCommandParserTopology(new MockSttClient(), new MockTranslateClient(), 0.90);

        var kafkaStreams = new KafkaStreams(voiceParserTopology.createTopology(), streamsConfiguration.streamsConfiguration());

        kafkaStreams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(kafkaStreams::close));
    }

}
