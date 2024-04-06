package org.example.voiceparser.services;

import org.example.voiceparser.model.ParsedVoiceCommand;
import org.example.voiceparser.model.VoiceCommand;

public interface SpeechToTextService {
    ParsedVoiceCommand speechToText(VoiceCommand voiceCommand);
}
