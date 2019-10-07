package edu.usfca.cs.dfs.timer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.usfca.cs.Utils;
import edu.usfca.cs.db.SqlManager;
import edu.usfca.cs.db.model.StorageNode;
import edu.usfca.cs.dfs.DfsControllerStarter;
import edu.usfca.cs.dfs.StorageMessages;
import edu.usfca.cs.dfs.config.ConfigurationManagerController;
import edu.usfca.cs.dfs.config.Constants;
import edu.usfca.cs.dfs.net.MessagePipeline;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

public class KeepAliveCheckTimerTask implements Runnable {

    private static Logger logger = LogManager.getLogger(KeepAliveCheckTimerTask.class);
    private int           snId;
    private long          timeOut;

    public KeepAliveCheckTimerTask(int snId) {
        this.snId = snId;
        this.timeOut = ConfigurationManagerController.getInstance()
                .getHeartBeatTimeoutInMilliseconds();
    }

    private void backup(HashMap<Integer, StorageNode> availableSNs) {
        int backupId = -1;
        SqlManager sqlManager = SqlManager.getInstance();
        Random rand = new Random();
        List<StorageNode> listSN = new ArrayList<StorageNode>(availableSNs.values());
        while (backupId == -1) {
            int index = rand.nextInt(availableSNs.size());
            StorageNode sn = listSN.get(index);
            backupId = sn.getSnId();
        }
        StorageNode backupNode = sqlManager.getSNInformationById(backupId);
        sqlManager.updateSNReplication(snId, backupNode.getSnId());

        //Backup data of current node
        //Send current down SN data to backup ID
        StorageNode downNode = sqlManager.getSNReplication(snId);
        ArrayList<Integer> replicateIdList = downNode.getReplicateSnIdList();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        MessagePipeline pipeline = new MessagePipeline(Constants.CONTROLLER);

        Bootstrap bootstrap = new Bootstrap().group(workerGroup).channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true).handler(pipeline);
        for (int replicateId : replicateIdList) {
            StorageNode snNode = sqlManager.getSNInformationById(replicateId);
            if (snNode.getStatus().equals("DOWN")) {
                continue;
            } else {
                ChannelFuture cf = Utils.connect(bootstrap, snNode.getSnIp(), snNode.getSnPort());
                StorageMessages.BackUp backUpMsg = StorageMessages.BackUp.newBuilder()
                        .setDestinationIp(backupNode.getSnIp())
                        .setDestinationPort(backupNode.getSnPort()).setSourceSnId(snId).build();
                StorageMessages.StorageMessageWrapper msgWrapper = StorageMessages.StorageMessageWrapper
                        .newBuilder().setBackup(backUpMsg).build();
                System.out.printf("Request data of %d send to replica: %d\n", snId, replicateId);
                cf.channel().writeAndFlush(msgWrapper).syncUninterruptibly();
                break;
            }
        }

        //Source of replication
        //Send replicate data of current down SN to backup ID
        downNode = sqlManager.getSourceReplicationSnId(snId);
        ArrayList<Integer> sourceIdList = downNode.getSourceSnIdList();
        for (int sourceId : sourceIdList) {
            System.out.println("Source Id: " + sourceId);
            StorageNode sourceNode = sqlManager.getSNInformationById(sourceId);
            String fromIp = "";
            int fromPort = 0;
            if (sourceNode.getStatus().equals("DOWN")) {//SourceNode down, get data from sourceNode replica
                ArrayList<Integer> sourceReplicaIdList = sqlManager.getSNReplication(sourceId)
                        .getReplicateSnIdList();
                for (int sourceReplicaId : sourceReplicaIdList) {
                    StorageNode sourceReplication = sqlManager
                            .getSNInformationById(sourceReplicaId);
                    if (!sourceReplication.getStatus().equals("DOWN")) {
                        fromIp = sourceReplication.getSnIp();
                        fromPort = sourceReplication.getSnPort();
                        break;
                    }
                }
            } else {
                fromIp = sourceNode.getSnIp();
                fromPort = sourceNode.getSnPort();
            }
            if (!fromIp.isEmpty() && fromPort != 0) {
                ChannelFuture cf = Utils.connect(bootstrap, fromIp, fromPort);
                StorageMessages.BackUp backUpMsg = StorageMessages.BackUp.newBuilder()
                        .setDestinationIp(backupNode.getSnIp())
                        .setDestinationPort(backupNode.getSnPort()).setSourceSnId(sourceId).build();
                StorageMessages.StorageMessageWrapper msgWrapper = StorageMessages.StorageMessageWrapper
                        .newBuilder().setBackup(backUpMsg).build();
                System.out
                        .printf("Request data of %d send to source port %d\n", sourceId, fromPort);
                cf.channel().writeAndFlush(msgWrapper).syncUninterruptibly();
            } else {
                System.out.printf("[Controller][BackUp] All source of data %d down!\n", sourceId);
            }
        }
    }

    public void run() {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Apply Keep Alive Checker for snId :" + snId);
            }
            DfsControllerStarter dfsControllerStarter = DfsControllerStarter.getInstance();
            StorageNode storageNode = dfsControllerStarter.getStorageNodeHashMap().get(snId);
            //Only check if SN is OPERATIONAL
            if (storageNode != null
                    && storageNode.getStatus().equals(Constants.STATUS_OPERATIONAL)) {
                SqlManager sqlManager = SqlManager.getInstance();
                long currentTime = System.currentTimeMillis();

                if (logger.isDebugEnabled()) {
                    logger.info("SnId :" + snId + ",LastHeartBeatTime:"
                            + storageNode.getLastHeartBeatTime() + ",currenTime:" + currentTime
                            + ",timeout:" + timeOut);
                    logger.info("(currentTime - timeOut) :" + (currentTime - timeOut));
                    logger.debug("CurrenTime :" + currentTime);
                    logger.debug("LastHeartBeatTime:" + storageNode.getLastHeartBeatTime());
                    logger.debug("Timeout:" + timeOut);
                    logger.debug("(currentTime - timeOut) :" + (currentTime - timeOut));
                }
                if ((currentTime - timeOut) > storageNode.getLastHeartBeatTime()) {
                    System.out.println("Timeout occured for SN[" + snId + "], No heart beat since "
                            + timeOut + " milliseconds!");
                    storageNode.setStatus(Constants.STATUS_DOWN);
                    sqlManager.updateSNInformation(snId, Constants.STATUS_DOWN);
                    HashMap<Integer, StorageNode> availableSNs = sqlManager
                            .getAllSNByStatusList(Constants.STATUS_OPERATIONAL);
                    int numOfSn = dfsControllerStarter.getStorageNodeHashMap().size();
                    int lowerBound = Math.floorMod(snId - 2, numOfSn) == 0 ? numOfSn
                            : Math.floorMod(snId - 2, numOfSn);
                    int upperBound = Math.floorMod(snId + 2, numOfSn) == 0 ? numOfSn
                            : Math.floorMod(snId + 2, numOfSn);
                    System.out.println("Upper bound:" + upperBound);
                    System.out.println("Lower bound:" + lowerBound);
                    if (lowerBound < upperBound) {
                        for (int i = lowerBound; i <= upperBound; i++) {
                            System.out.println("Remove: " + i);
                            availableSNs.remove(i);
                        }
                    } else {
                        for (int i = lowerBound; i <= dfsControllerStarter.getStorageNodeHashMap()
                                .size(); i++) {
                            System.out.println("Remove: " + i);
                            availableSNs.remove(i);
                        }
                        for (int i = 1; i <= upperBound; i++) {
                            System.out.println("Remove: " + i);
                            availableSNs.remove(i);
                        }
                    }
                    if (availableSNs.size() == 0) {
                        System.out.println("No SN to replicate");
                        return;
                    }
                    this.backup(availableSNs);
                }
            } else {
                /**
                 * STRANGE BIG PROBLEM!!
                 */
            }
        } catch (Exception e) {
            logger.error("Exception occured in HeartBeat:", e);
        }
    }

}
