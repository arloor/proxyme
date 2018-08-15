package com.arloor.proxyme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpProxyBootStrap {

    private static Logger logger=LoggerFactory.getLogger(HttpProxyBootStrap.class.getSimpleName());

    public static void main(String[] args) {
        LocalSelector localSelector =LocalSelector.getInstance();
        Thread localSelectorThead=new Thread(localSelector,"localSelector");
        localSelectorThead.start();

        RemoteSelector remoteSelector=RemoteSelector.getInstance();
        Thread remoteSelectorThread=new Thread(remoteSelector,"remoteSlector");
        remoteSelectorThread.start();

        logger.info("在8080端口启动了代理服务");
    }
}
