package edu.usfca.cs.dfs.timer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.usfca.cs.db.model.StorageNode;
import edu.usfca.cs.dfs.DfsStorageNodeStarter;
import edu.usfca.cs.dfs.StorageMessages;
import edu.usfca.cs.dfs.config.ConfigurationManagerSn;
import io.netty.channel.Channel;

public class HeartBeatSenderTimerTask implements Runnable {

    private static Logger logger = LogManager.getLogger(HeartBeatSenderTimerTask.class);

    public HeartBeatSenderTimerTask() {
    }

    public void run() {
        try {
            StorageNode storageNode = DfsStorageNodeStarter.getInstance().getStorageNode();
            if (logger.isDebugEnabled()) {
                logger.debug("Heart Beat Timer triggered with snId :" + storageNode.getSnId());
            }
            logger.info("Heart Beat Timer triggered with snId :" + storageNode.getSnId());
            /**
             * SN will connect to the Controller
             */
            StorageMessages.HeartBeat heartBeat = StorageMessages.HeartBeat.newBuilder()
                    .setSnId(storageNode.getSnId())
                    .setSnIp(ConfigurationManagerSn.getInstance().getMyIp())
                    .setSnPort(ConfigurationManagerSn.getInstance().getSnPort())
                    .setTotalFreeSpaceInBytes(DfsStorageNodeStarter.getInstance()
                            .calculateTotalFreeSpaceInBytes())
                    .setNumOfRetrievelRequest((int) DfsStorageNodeStarter.getInstance()
                            .getStorageNode().getTotalRetrievelRequest())
                    .setNumOfStorageMessage((int) DfsStorageNodeStarter.getInstance()
                            .getStorageNode().getTotalStorageRequest())
                    .build();
            StorageMessages.StorageMessageWrapper msgWrapper = StorageMessages.StorageMessageWrapper
                    .newBuilder().setHeartBeatMsg(heartBeat).build();
            Channel chan = DfsStorageNodeStarter.getInstance().getChannelFuture().channel();
            chan.write(msgWrapper);
            chan.flush();

            logger.info("[Controller] ---------->>>>>>>> HEART BEAT To Controller, snId["
                    + heartBeat.getSnId() + "] >>>>>>>>>>>--------------");

        } catch (Exception e) {
            logger.error("Exception occured in HeartBeat:", e);
        }
    }
}
