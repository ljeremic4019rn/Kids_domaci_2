package servent.message.snapshot;

import app.ServentInfo;
import servent.message.BasicMessage;
import servent.message.MessageType;

import java.io.Serial;
import java.util.List;
import java.util.Map;

public class AbAskTokenMessage extends BasicMessage {

    @Serial
    private static final long serialVersionUID = -1189395068257017543L;

    public AbAskTokenMessage(ServentInfo sender, ServentInfo receiver, ServentInfo neighbor, Map<Integer, Integer> senderVectorClock) {
        super(MessageType.AB_ASK, sender, receiver, neighbor, senderVectorClock);
    }
}