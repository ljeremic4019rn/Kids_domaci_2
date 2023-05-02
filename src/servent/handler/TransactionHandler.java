package servent.handler;

import app.AppConfig;
import app.snapshot_bitcake.BitcakeManager;
import app.snapshot_bitcake.ab.AbBitCakeManager;
import app.snapshot_bitcake.av.AvBitCakeManager;
import servent.message.Message;
import servent.message.MessageType;

public class TransactionHandler implements MessageHandler {

    private Message clientMessage;
    private BitcakeManager bitcakeManager;

    public TransactionHandler(Message clientMessage, BitcakeManager bitcakeManager) {
        this.clientMessage = clientMessage;
        this.bitcakeManager = bitcakeManager;
    }

    @Override
    public void run() {
        if (clientMessage.getMessageType() == MessageType.TRANSACTION) {
            String amountString = clientMessage.getMessageText();

            int amountNumber = 0;
            try {
                amountNumber = Integer.parseInt(amountString);
            }
            catch (NumberFormatException e) {
                AppConfig.timestampedErrorPrint("Couldn't parse amount: " + amountString);
                return;
            }

            bitcakeManager.addSomeBitcakes(amountNumber);

            synchronized (AppConfig.syncHole) {
                if (bitcakeManager instanceof AbBitCakeManager) {
                    AbBitCakeManager abBitcakeManager = (AbBitCakeManager) bitcakeManager;
                    //todo proveri
                    abBitcakeManager.recordGetTransaction(clientMessage.getOriginalSenderInfo().getId(), amountNumber);
                }
                else if (bitcakeManager instanceof AvBitCakeManager) {
                    AvBitCakeManager avBitcakeManager = (AvBitCakeManager) bitcakeManager;
                    //todo proveri
                    avBitcakeManager.recordGetTransaction(clientMessage.getSenderVectorClock(), clientMessage.getOriginalSenderInfo().getId(), amountNumber);
                }
            }
        }
        else {
            AppConfig.timestampedErrorPrint("Transaction handler got: " + clientMessage);
        }

        AppConfig.timestampedErrorPrint("Transaction handler got: " + clientMessage);
    }


}
