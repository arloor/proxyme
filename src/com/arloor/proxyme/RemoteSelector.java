package com.arloor.proxyme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;


public class RemoteSelector implements Runnable {
    private static Logger logger = LoggerFactory.getLogger(RemoteSelector.class);
    private Selector selectorRemote;
    private final static RemoteSelector instance = new RemoteSelector();
    //这个队列用于保存在LocalSelector线程中创建的到远程服务器的channel
    //为什么要这样？因为localSelector和remoteSelector是两个线程。
    //而remoteSeletor在select是会阻塞。阻塞时占有锁（在源码中是所在了publicKeys）。
    //而channel向selector注册时，也需要占有这个锁。最终结果就是register会被阻塞。
    //这就导致了，响应一直到不了。
    private ConcurrentLinkedQueue<ChannalBridge> waitRegisterBridges = new ConcurrentLinkedQueue<>();

    public static RemoteSelector getInstance() {
        return instance;
    }

    private RemoteSelector() {
        try {
            this.selectorRemote = Selector.open();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void listenRemoteChannel(SocketChannel remoteChannal, ChannalBridge channalBridge) {
        try {
            remoteChannal.register(selectorRemote, SelectionKey.OP_READ, channalBridge);
            channalBridge.setRemoteSelectionKey(remoteChannal.keyFor(selectorRemote));
        } catch (ClosedChannelException e) {
            e.printStackTrace();
        }
    }

    private void listen() {

        while (true) {
            try {
                //注册remoteChannal
                ChannalBridge waitRegisterBridge;
                while ((waitRegisterBridge = waitRegisterBridges.poll()) != null) {
                    logger.info("注册remoteChannel到remoteSelector。remoteChannel: " + waitRegisterBridge.getRemoteChannal().getRemoteAddress());
                    this.listenRemoteChannel(waitRegisterBridge.getRemoteChannal(), waitRegisterBridge);
                }
                //循环selctor
                int numKey = selectorRemote.select();
                if (numKey > 0) {
                    Set<SelectionKey> keys = selectorRemote.selectedKeys();
                    Iterator<SelectionKey> keyIterator = keys.iterator();
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        if (key.isReadable()) {//读来自远程服务器的响应
                            ChannalBridge channalBridge = (ChannalBridge) key.attachment();
                            channalBridge.readRemoteAndSendToLocal();
                        }
                        keyIterator.remove();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public void addWaitRegistBridge(ChannalBridge channalBridge) {
        waitRegisterBridges.add(channalBridge);
        selectorRemote.wakeup();
    }

    @Override
    public void run() {
        listen();
    }


}
