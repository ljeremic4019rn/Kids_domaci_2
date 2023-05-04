package app;

import app.snapshot_bitcake.SnapshotCollector;
import servent.handler.TransactionHandler;
import servent.handler.snapshot.AbAskHandler;
import servent.handler.snapshot.AbTellHandler;
import servent.message.BasicMessage;
import servent.message.Message;
import servent.message.MessageType;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;


public class CausalBroadcastShared {
    private static final Map<Integer, Integer> vectorClock = new ConcurrentHashMap<>();
    private static final List<Message> commitedCausalMessageList = new CopyOnWriteArrayList<>();
    private static final Queue<Message> pendingMessages = new ConcurrentLinkedQueue<>();
    private static final Object pendingMessagesLock = new Object();
    private static final ExecutorService committedMessagesThreadPool = Executors.newWorkStealingPool();
    private static SnapshotCollector snapshotCollector;

    private static final List<Message> sendTransactions = new CopyOnWriteArrayList<>();
    private static final List<Message> receivedTransactions = new CopyOnWriteArrayList<>();
    private static final Set<Message> receivedAbAsk = Collections.newSetFromMap(new ConcurrentHashMap<>());


    public static void initializeVectorClock(int serventCount) {
        for (int i = 0; i < serventCount; i++) {
            vectorClock.put(i, 0);
        }
    }

    public static void incrementClock(int serventId) {
        vectorClock.computeIfPresent(serventId, new BiFunction<Integer, Integer, Integer>() {

            @Override
            public Integer apply(Integer key, Integer oldValue) {
                return oldValue + 1;
            }
        });
    }

    public static Map<Integer, Integer> getVectorClock() {
        return vectorClock;
    }

    public static SnapshotCollector getSnapshotCollector() {
        return snapshotCollector;
    }

    public static void setSnapshotCollector(SnapshotCollector snapshotCollector) {
        CausalBroadcastShared.snapshotCollector = snapshotCollector;
    }

    public static void addPendingMessage(Message msg) {
        pendingMessages.add(msg);
    }

    private static boolean otherClockGreater(Map<Integer, Integer> clock1, Map<Integer, Integer> clock2) {
        if (clock1.size() != clock2.size()) {
            throw new IllegalArgumentException("Clocks are not same size how why");
        }

        for (int i = 0; i < clock1.size(); i++) {
            if (clock2.get(i) > clock1.get(i)) {
                return true;
            }
        }

        return false;
    }


    public static void addReceivedTransaction(Message receivedTransaction) {
        receivedTransactions.add(receivedTransaction);
    }

    public static List<Message> getReceivedTransactions() {
        return receivedTransactions;
    }

    public static void addSendTransaction(Message sendTransaction) {
        sendTransactions.add(sendTransaction);
    }

    public static List<Message> getSendTransactions() {
        return sendTransactions;
    }

    public static List<Message> getCommitedCausalMessages() {
        List<Message> toReturn = new CopyOnWriteArrayList<>(commitedCausalMessageList);

        return toReturn;
    }

    public static void commitCausalMessage(Message newMessage) {
        AppConfig.timestampedStandardPrint("-Committing- " + newMessage);
//        commitedCausalMessageList.add(newMessage);
        incrementClock(newMessage.getOriginalSenderInfo().getId());

        checkPendingMessages();
    }


    public static void checkPendingMessages() {
        boolean gotWork = true;

        while (gotWork) {
            gotWork = false;

            synchronized (pendingMessagesLock) {
                Iterator<Message> iterator = pendingMessages.iterator();
                Map<Integer, Integer> myVectorClock = getVectorClock();

                while (iterator.hasNext()) {
                    Message pendingMessage = iterator.next();
                    BasicMessage basicMessage = (BasicMessage) pendingMessage;

                    if (!otherClockGreater(myVectorClock, basicMessage.getSenderVectorClock())) {//ako je desni (sender) veci od mene (levi) imamo message
                        gotWork = true;

                        AppConfig.timestampedStandardPrint("Committing " + pendingMessage);
                        commitedCausalMessageList.add(pendingMessage);
                        incrementClock(pendingMessage.getOriginalSenderInfo().getId());

                        boolean didPut;

                        switch (basicMessage.getMessageType()){
                            case TRANSACTION -> {
                                if (basicMessage.getOriginalReceiverInfo().getId() == AppConfig.myServentInfo.getId())
                                    committedMessagesThreadPool.submit(new TransactionHandler(basicMessage, snapshotCollector.getBitcakeManager()));
                            }
                            case AB_ASK -> {//todo check
                                didPut = receivedAbAsk.add(basicMessage);
                                if (didPut) committedMessagesThreadPool.submit(new AbAskHandler(basicMessage, snapshotCollector));
                            }
                            case AB_TELL -> {//todo check
                                if (basicMessage.getOriginalReceiverInfo().getId() == AppConfig.myServentInfo.getId())
                                    committedMessagesThreadPool.submit(new AbTellHandler(basicMessage, snapshotCollector));

                            }
                            case AV_ASK -> {}//todo av ask
                            case AV_TELL -> {}//todo av tell
                        }

                        iterator.remove();
                        break;
                    }
                }
            }
        }
    }

}
