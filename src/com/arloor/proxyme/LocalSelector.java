package com.arloor.proxyme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

public class LocalSelector implements Runnable {
    private static Logger logger = LoggerFactory.getLogger(LocalSelector.class.getSimpleName());
    private Selector selectorLocal;
    private ServerSocketChannel acceptChannel;
    private final static LocalSelector instance = new LocalSelector();

    public static LocalSelector getInstance() {
        return instance;
    }


    private LocalSelector() {

    }

    public void init(String host,int port){
        try {
            this.selectorLocal = Selector.open();
            acceptChannel = ServerSocketChannel.open();
            InetSocketAddress serverSocketAddr=new InetSocketAddress(host, port);
            if(host.equals("localhost")){
                serverSocketAddr=new InetSocketAddress(InetAddress.getLocalHost(),port);
            }
                logger.info("在"+serverSocketAddr.getAddress().getHostAddress()+":"+port+"端口启动了代理服务。注意可能非127.0.0.1");
            acceptChannel.bind(serverSocketAddr);
            acceptChannel.configureBlocking(false);
            acceptChannel.register(selectorLocal, SelectionKey.OP_ACCEPT);
            } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void listen() {
        while (true) {
            //循环selctor
            try {
                int numKey = selectorLocal.select();
                if (numKey > 0) {
                    Set<SelectionKey> keys = selectorLocal.selectedKeys();
                    Iterator<SelectionKey> keyIterator = keys.iterator();
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        try{
                            if (key.isReadable()) {//读来自浏览器的请求
                                ChannalBridge channalBridge = (ChannalBridge) key.attachment();
                                channalBridge.readLocal();
                            }else if (key.isAcceptable()) {//proxyChannel接收来自浏览器的连接
                                accept();
                            }
                        }catch (CancelledKeyException e){
                            logger.warn("selection已经关闭，不处理");
                        }
                        keyIterator.remove();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }


    public void accept() {
        try {
            SocketChannel localChanel = acceptChannel.accept();
            localChanel.configureBlocking(false);
            logger.info("接收浏览器连接: " + localChanel.getRemoteAddress());
            ChannalBridge channalBridge = new ChannalBridge(localChanel);
            this.listenLocalChannel(localChanel, channalBridge);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void listenLocalChannel(SocketChannel localChanel, ChannalBridge channalBridge) {
        try {
            localChanel.register(selectorLocal, SelectionKey.OP_READ, channalBridge);
            channalBridge.setLocalSelectionKey(localChanel.keyFor(selectorLocal));
        } catch (ClosedChannelException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        this.listen();
    }


}
