package com.arloor.proxyme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpProxyBootStrap {

    private static Logger logger = LoggerFactory.getLogger(HttpProxyBootStrap.class.getSimpleName());

    public static void main(String[] args) {
        int port = 8080;
        String host = "localhost";
        try {
            for (String arg : args
            ) {
                String[] nameValue = arg.split("=");
                if (nameValue[0].equals("host") && nameValue[1].replace(" ","").length()>0) {
                    host = nameValue[1];
                }
                if (nameValue[0].equals("port") && nameValue[1].replace(" ","").length()>0) {
                    port = Integer.parseInt(nameValue[1]);
                }
            }
        } catch (Exception e) {
            logger.error("请注意参数格式： host=xxx port=1234");
        }
        if (args.length <2) {
            logger.warn("提示：允许携带两个命令行参数 host=xxx port=1234");
        }
        LocalSelector localSelector = LocalSelector.getInstance();
        localSelector.init(host, port);
        Thread localSelectorThead = new Thread(localSelector, "localSelector");
        localSelectorThead.start();

        RemoteSelector remoteSelector = RemoteSelector.getInstance();
        Thread remoteSelectorThread = new Thread(remoteSelector, "remoteSlector");
        remoteSelectorThread.start();


    }
}
