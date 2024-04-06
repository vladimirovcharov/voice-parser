package org.example.voiceparser;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.example.voiceparser.model.ParsedVoiceCommand;
import org.example.voiceparser.model.VoiceCommand;
import org.example.voiceparser.serdes.JsonSerde;
import org.example.voiceparser.services.SpeechToTextService;
import org.example.voiceparser.services.TranslateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Properties;
import java.util.Random;
import java.util.UUID;

import static org.example.voiceparser.VoiceCommandParserTopology.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VoiceCommandParserTopologyTest {
    public static final double THRESHOLD = 0.90;

    private TestInputTopic<String, VoiceCommand> voiceCommandInputTopic;
    private TestOutputTopic<String, ParsedVoiceCommand> recognizedCommandsOutputTopic;
    private TestOutputTopic<String, ParsedVoiceCommand> unrecognizedCommandsOutputTopic;

    @Mock
    private SpeechToTextService speechToTextService;
    @Mock
    private TranslateService translateService;

    @BeforeEach
    void setUp() {
        var voiceCommandParserTopology = new VoiceCommandParserTopology(speechToTextService, translateService, THRESHOLD);
        var topology = voiceCommandParserTopology.createTopology();
        var props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        TopologyTestDriver topologyTestDriver = new TopologyTestDriver(topology, props);

        var voiceCommandJsonSerde = new JsonSerde<>(VoiceCommand.class);
        var parsedVoiceCommandJsonSerde = new JsonSerde<>(ParsedVoiceCommand.class);

        voiceCommandInputTopic = topologyTestDriver.createInputTopic(VOICE_COMMANDS_TOPIC, Serdes.String().serializer(), voiceCommandJsonSerde.serializer());
        recognizedCommandsOutputTopic = topologyTestDriver.createOutputTopic(RECOGNIZED_COMMANDS_TOPIC, Serdes.String().deserializer(), parsedVoiceCommandJsonSerde.deserializer());
        unrecognizedCommandsOutputTopic = topologyTestDriver.createOutputTopic(UNRECOGNIZED_COMMANDS_TOPIC, Serdes.String().deserializer(), parsedVoiceCommandJsonSerde.deserializer());
    }

    @Test
    @DisplayName("Given an English voice command, When processed correctly Then I receive a ParsedVoiceCommand in the recognnized-commands topic.")
    void test1() {
        var bytes = new byte[20];
        new Random().nextBytes(bytes);
        var voiceCommand = VoiceCommand.builder().id(UUID.randomUUID().toString()).audio(bytes)
                .audioCodec("FLAC").language("en-US").build();

        var mockParsedVoiceCommand = ParsedVoiceCommand.builder()
                .id(voiceCommand.getId())
                .language("en-US")
                .probability(0.98)
                .text("call John").build();
        given(speechToTextService.speechToText(voiceCommand)).willReturn(mockParsedVoiceCommand);

        voiceCommandInputTopic.pipeInput(voiceCommand);

        var parsedVoiceCommand = recognizedCommandsOutputTopic.readRecord().value();

        assertEquals(voiceCommand.getId(), parsedVoiceCommand.getId());
        assertEquals("call John", parsedVoiceCommand.getText());
    }

    @Test
    @DisplayName("Given a non-English voice command, When processed correctly Then I receive a ParsedVoiceCommand in the recognnized-commands topic.")
    void test2() {
        var bytes = new byte[20];
        new Random().nextBytes(bytes);
        var voiceCommand = VoiceCommand.builder().id(UUID.randomUUID().toString()).audio(bytes)
                .audioCodec("FLAC").language("es-AR").build();

        var mockParsedVoiceCommand = ParsedVoiceCommand.builder()
                .id(voiceCommand.getId())
                .language("es-AR")
                .probability(0.98)
                .text("llamar a Juan").build();
        given(speechToTextService.speechToText(voiceCommand)).willReturn(mockParsedVoiceCommand);

        var mockTranslatedVoiceCommand = ParsedVoiceCommand.builder()
                .id(voiceCommand.getId())
                .language("en-US")
                .probability(0.98)
                .text("call John").build();
        given(translateService.translate(mockParsedVoiceCommand)).willReturn(mockTranslatedVoiceCommand);

        voiceCommandInputTopic.pipeInput(voiceCommand);

        var parsedVoiceCommand = recognizedCommandsOutputTopic.readRecord().value();

        assertEquals(voiceCommand.getId(), parsedVoiceCommand.getId());
        assertEquals("call John", parsedVoiceCommand.getText());
    }

    @Test
    @DisplayName("Given a non-recognizable voice command, When processed correctly Then I receive a ParsedVoiceCommand in the unrecognnized-commands topic.")
    void test3() {
        var bytes = new byte[20];
        new Random().nextBytes(bytes);
        var voiceCommand = VoiceCommand.builder()
                .id(UUID.randomUUID().toString())
                .audio(bytes)
                .audioCodec("FLAC")
                .language("en-US").build();

        var parsedVoiceCommand = ParsedVoiceCommand.builder()
                .id(voiceCommand.getId())
                .language("en-US")
                .probability(0.30)
                .text("call John").build();
        given(speechToTextService.speechToText(voiceCommand)).willReturn(parsedVoiceCommand);

        voiceCommandInputTopic.pipeInput(voiceCommand);

        var actualVoiceCommand = unrecognizedCommandsOutputTopic.readRecord().value();

        assertTrue(recognizedCommandsOutputTopic.isEmpty());
        assertEquals(voiceCommand.getId(), actualVoiceCommand.getId());
        verify(translateService, never()).translate(any(ParsedVoiceCommand.class));
    }

    @Test
    @DisplayName("Given voice command that is too short (less than 10 bytes), When processed correctly Then I don’t receive any command in any of the output topics.")
    void test4() {
        var bytes = new byte[9];
        new Random().nextBytes(bytes);
        var voiceCommand = VoiceCommand.builder()
                .id(UUID.randomUUID().toString())
                .audio(bytes)
                .audioCodec("FLAC")
                .language("en-US").build();

        voiceCommandInputTopic.pipeInput(voiceCommand);

        assertTrue(recognizedCommandsOutputTopic.isEmpty());
        assertTrue(unrecognizedCommandsOutputTopic.isEmpty());
        verify(speechToTextService, never()).speechToText(any(VoiceCommand.class));
        verify(translateService, never()).translate(any(ParsedVoiceCommand.class));
    }
}