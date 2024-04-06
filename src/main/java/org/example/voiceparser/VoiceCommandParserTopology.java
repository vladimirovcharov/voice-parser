package org.example.voiceparser;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import org.example.voiceparser.model.ParsedVoiceCommand;
import org.example.voiceparser.model.VoiceCommand;
import org.example.voiceparser.serdes.JsonSerde;
import org.example.voiceparser.services.SpeechToTextService;
import org.example.voiceparser.services.TranslateService;

import java.util.Map;

public class VoiceCommandParserTopology {
    public static final String VOICE_COMMANDS_TOPIC = "voice-commands";
    public static final String RECOGNIZED_COMMANDS_TOPIC = "recognized-commands";
    public static final String UNRECOGNIZED_COMMANDS_TOPIC = "unrecognized-commands";

    private final SpeechToTextService speechToTextService;
    private final TranslateService translateService;
    private final Double certaintyThreshold;

    public VoiceCommandParserTopology(SpeechToTextService speechToTextService, TranslateService translateService, Double certaintyThreshold) {
        this.speechToTextService = speechToTextService;
        this.translateService = translateService;
        this.certaintyThreshold = certaintyThreshold;
    }

    public Topology createTopology() {
        var streamsBuilder = new StreamsBuilder();

        var voiceCommandJsonSerde = new JsonSerde<>(VoiceCommand.class);
        var parsedVoiceCommandJsonSerde = new JsonSerde<>(ParsedVoiceCommand.class);

        Map<String, KStream<String, ParsedVoiceCommand>> branchesMap = streamsBuilder.stream(VOICE_COMMANDS_TOPIC, Consumed.with(Serdes.String(), voiceCommandJsonSerde))
                .filter((key, value) -> value.getAudio().length > 10)
                .mapValues((readOnlyKey, voiceCommand) -> speechToTextService.speechToText(voiceCommand))
                .split(Named.as("branches-"))
                .branch((key, voiceCommand) -> voiceCommand.getProbability() > certaintyThreshold, Branched.as("recognized"))
                .defaultBranch(Branched.as("not-recognized"));

        branchesMap.get("branches-not-recognized")
                .to(UNRECOGNIZED_COMMANDS_TOPIC, Produced.with(Serdes.String(), parsedVoiceCommandJsonSerde));

        Map<String, KStream<String, ParsedVoiceCommand>> langStreams = branchesMap.get("branches-recognized")
                .split(Named.as("lang-"))
                .branch((key, voiceCommand) -> voiceCommand.getLanguage().startsWith("en"), Branched.as("en"))
                .defaultBranch(Branched.as("other"));

        langStreams.get("lang-other")
                .mapValues((readOnlyKey, voiceCommand) -> translateService.translate(voiceCommand))
                .merge(langStreams.get("lang-en"))
                .to(RECOGNIZED_COMMANDS_TOPIC, Produced.with(Serdes.String(), parsedVoiceCommandJsonSerde));

        return streamsBuilder.build();
    }
}
