package com.arloor.proxyme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpProxyBootStrap {

    private static Logger logger=LoggerFactory.getLogger(HttpProxyBootStrap.class.getSimpleName());

    public static void main(String[] args) {
        int port=8080;
        if(args.length==1){
            try {
                port=Integer.parseInt(args[0]);
            }catch (Exception e){
                logger.error("参数请输入端口，int");
            }
        }else {
            logger.warn("提示：允许携带一个命令行参数—端口号");
        }
        LocalSelector localSelector =LocalSelector.getInstance();
        localSelector.init(port);
        Thread localSelectorThead=new Thread(localSelector,"localSelector");
        localSelectorThead.start();


        RemoteSelector remoteSelector=RemoteSelector.getInstance();
        Thread remoteSelectorThread=new Thread(remoteSelector,"remoteSlector");
        remoteSelectorThread.start();


    }
}
