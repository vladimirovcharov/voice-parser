package org.example.voiceparser.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceCommand {
    private String id;
    private byte[] audio;
    private String audioCodec;
    private String language;
}
