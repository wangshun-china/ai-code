package com.ws.codecraft.ai.model.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class StageMessage extends StreamMessage {

    private String stage;
    private String label;

    public StageMessage(String stage, String label) {
        super(StreamMessageTypeEnum.STAGE.getValue());
        this.stage = stage;
        this.label = label;
    }
}
