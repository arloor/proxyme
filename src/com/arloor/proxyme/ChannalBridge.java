package com.arloor.proxyme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.regex.Pattern;

public class ChannalBridge {
    private SocketChannel localChannel;
    private volatile SocketChannel remoteChannel;
    private final ByteBuffer local2RemoteBuff = ByteBuffer.allocate(8096);
    private final ByteBuffer remote2LocalBuff = ByteBuffer.allocate(8096);
    private SelectionKey localSelectionKey;
    private SelectionKey remoteSelectionKey;
    private String host;
    private int port;
    private boolean isHttpsTunnel = false;//标记位 这个bridge是否是https隧道

    private static Logger logger = LoggerFactory.getLogger(ChannalBridge.class.getSimpleName());

    public ChannalBridge(SocketChannel localChannel) {
        this.localChannel = localChannel;
    }

    public void readLocal() {
        try {
            int numRead = 0;
            try {
                numRead = localChannel.read(local2RemoteBuff);
            } catch (IOException e) {
                //todo:应该是出现了"你的主机中的软件中止了一个已建立的连接。"的异常  研究为什么会出现这个问题
                //一篇博文：https://blog.csdn.net/abc_key/article/details/29295569
                logger.warn("selector通知localChannel可读，但在读的过程中抛出异常。大概率异常信息为：“你的主机中的软件中止了一个已建立的连接。”。可以认为是这个localChannel出现异常，下面关闭这个localChannel。");
                logger.warn("异常信息: " + e.getMessage() + "  是不是上面说的？");
                localSelectionKey.cancel();
                localChannel.close();
            }
            if (numRead > 0) {
                sendToRemote(numRead);
            } else if (numRead == 0) {
                remote2LocalBuff.clear();
            } else if (numRead == -1) {
                localChannel.close();
                localSelectionKey.cancel();
                remote2LocalBuff.clear();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendToRemote(int numRead) throws IOException {
        byte[] readBytes = local2RemoteBuff.array();
        local2RemoteBuff.clear();

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(readBytes);
        BufferedReader br = new BufferedReader(new InputStreamReader(byteArrayInputStream));
        String line = br.readLine();
        //如果是request line 例如： GET http://detectportal.firefox.com/success.txt HTTP/1.1
        if (Pattern.matches(".* .* HTTP.*", line)) {
            logger.info("请求—— " + line);

            String[] requestLineCells = line.split(" ");
            String method = requestLineCells[0];
            String urlstr = requestLineCells[1];
            String protocal = requestLineCells[2];
            RequestHeader requestHeader = new RequestHeader(method, urlstr, protocal);
            while ((line = br.readLine()) != null && line.length() != 0) {
//                    System.out.println(line);
                String[] key_value = line.split(":");
                requestHeader.setHeader(key_value[0], key_value[1]);
            }
            if (!isHttpsTunnel && !method.equals("CONNECT")) {
                if (remoteChannel == null) {
                    requestHeader.reform();
                    //创建remoteChannel
                    if (!connectToRemote(requestHeader.getHost(), requestHeader.getPort())) {
                        return;
                    }
                }
                sendHeadersToRemote(requestHeader);
                sendBodyToRemote(readBytes, numRead);
            } else {//这是一个https的connect隧道请求。method="CONNECT"
                isHttpsTunnel = true;
                String host = urlstr.split(":")[0];
                String portStr = urlstr.split(":")[1];
                int port = Integer.parseInt(portStr);
                if (connectToRemote(host, port)) {
                    //
                    remote2LocalBuff.put(ResponseHelper.httpsTunnelEstablished());
                    remote2LocalBuff.flip();
                    localChannel.write(remote2LocalBuff);
                    remote2LocalBuff.clear();
                    sendBodyToRemote(readBytes, numRead);
                } else {
                    return;
                }
            }
        } else {
            sendToRemoteWildly(readBytes, numRead);
        }
    }

    private void sendBodyToRemote(byte[] readBytes, int numRead) {
        try {
            byte[] body = getRequestBody(readBytes, numRead);
            if (body != null) {
                local2RemoteBuff.put(body);
                writeBuffToRemoteChannel(numRead);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private boolean connectToRemote(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        try {
            //设置连接超时时间1s
            remoteChannel=SocketChannel.open();
            remoteChannel.socket().connect(new InetSocketAddress(host, port),700);
//未作超时处理            remoteChannel = SocketChannel.open(new InetSocketAddress(host, port));
        }catch (SocketTimeoutException e){
            //健壮性：如果无法连接远程服务器，不做处理这个线程会退出
            logger.warn("连接超时 -->"+host+"，返回404响应，关闭localChannel");
            //给浏览器一个404的返回。如果不返回。。它会等很久。。并且还会重试
            byte[] response404 = ResponseHelper.http404();
            local2RemoteBuff.put(response404);
            local2RemoteBuff.flip();
            localChannel.write(local2RemoteBuff);
            local2RemoteBuff.clear();
            localChannel.close();
            localSelectionKey.cancel();
            return false;
        }catch (Exception e) {

            //健壮性：如果无法连接远程服务器，不做处理这个线程会退出
            logger.warn("连接失败 -->"+host+"，返回404响应，关闭localChannel");
            //给浏览器一个404的返回。如果不返回。。它会等很久。。并且还会重试
            byte[] response404 = ResponseHelper.http404();
            local2RemoteBuff.put(response404);
            local2RemoteBuff.flip();
            localChannel.write(local2RemoteBuff);
            local2RemoteBuff.clear();
            localChannel.close();
            localSelectionKey.cancel();
            return false;
        }
        remoteChannel.configureBlocking(false);
        logger.info("创建远程连接: " + remoteChannel.getRemoteAddress());
        RemoteSelector remoteSelector = RemoteSelector.getInstance();
        remoteSelector.addWaitRegistBridge(this);
        return true;
    }

    private void sendToRemoteWildly(byte[] readBytes, int numRead) {
        //不含http请求头（是未完请求的剩余部分）或者是https流量
        //直接向remote写
        try {
            local2RemoteBuff.put(readBytes);
            writeBuffToRemoteChannel(numRead);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void sendHeadersToRemote(RequestHeader requestHeader) {
        try {

            byte[] requestHeaderBytes = requestHeader.toBytes();
            local2RemoteBuff.put(requestHeaderBytes);
            writeBuffToRemoteChannel(requestHeaderBytes.length);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeBuffToRemoteChannel(int numRead) throws IOException {
        local2RemoteBuff.flip();//flip很关键 不flip 408错误
//        while (remoteChannel==null){
//            try {
//                Thread.sleep(200);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
        local2RemoteBuff.limit(numRead);
        try {
            if (remoteChannel == null) {
                return;
            }
            while (local2RemoteBuff.hasRemaining()) {
                int writeNum = remoteChannel.write(local2RemoteBuff);
                logger.info("发送请求" + writeNum + " -->" + remoteChannel.getRemoteAddress());
                if (writeNum == 0) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

        } catch (NullPointerException e) {
            if (remoteChannel == null) {
                logger.warn("试图写已为null的remoteChannel：" + e.getMessage() + "——已经解决了这个问题，就是在试图写到remoteChnanel前判断是否为null");
            } else {
                e.printStackTrace();
            }
            return;
        } catch (ClosedChannelException e) {
            e.printStackTrace();
            logger.warn("试图写已关闭的remoteChannel：" + e.getMessage() + "——已经解决了这个问题，就是remoteChannel close之后的=null。");
            //保险起见，但应该不会执行这四句
            remoteChannel.close();
            remoteSelectionKey.cancel();
            remoteChannel = null;
            remoteSelectionKey = null;
        } catch (IOException e) {
            e.printStackTrace();
            logger.warn("大概率出现了如下异常: 远程主机强迫关闭了一个现有的连接。看来是远程服务器的socket被异常关闭了。下面关闭这个远程socketChannal");
            logger.warn("异常信息： " + e.getMessage() + " 是不是上面说的？");
            remoteChannel.close();
            remoteSelectionKey.cancel();
            remoteChannel = null;
            remoteSelectionKey = null;
        } finally {
            local2RemoteBuff.clear();
        }
    }

    public void readRemoteAndSendToLocal() {
        try {
            int readNum = 0;
            try {
                readNum = remoteChannel.read(remote2LocalBuff);
            } catch (IOException e) {
                //todo:应该是出现了"远程主机强迫关闭了一个现有的连接。"的异常 研究为什么会出现这个问题
                //一篇博文：https://blog.csdn.net/abc_key/article/details/29295569
                logger.warn("selector通知remoteChannel可读，但在读的过程中抛出异常。大概率异常信息为：“远程主机强迫关闭了一个现有的连接。”。可以认为是这个remoteChannel出现异常，下面关闭这个remoteChannel。");
                logger.warn("异常信息: " + e.getMessage() + "  是不是上面说的？");
                remoteSelectionKey.cancel();
                remoteChannel.close();
                remoteChannel = null;
                remoteSelectionKey = null;
            }
            if (readNum > 0) {
                logger.info("接收响应" + readNum + " <--" + remoteChannel.getRemoteAddress());
                remote2LocalBuff.flip();
//                System.out.println(new String(remote2LocalBuff.array()));
                try {
                    while (remote2LocalBuff.hasRemaining()) {
                        int writeNum = localChannel.write(remote2LocalBuff);
                        if (writeNum == 0) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } catch (ClosedChannelException e) {
                    logger.warn("过期的响应，本地channel已经关闭。清理过期的远程channel");
                    remoteChannel.close();
                    remoteSelectionKey.cancel();
                    remoteChannel = null;
                    remoteSelectionKey = null;
                } catch (IOException e) {
                    logger.warn("大概率出现了如下异常: 你的主机中的软件中止了一个已建立的连接。看来是本地浏览器的socket被异常关闭了。下面关闭这个本地socketChannal");
                    logger.warn("异常信息： " + e.getMessage() + " 是不是上面说的？");
                    localChannel.close();
                    localSelectionKey.cancel();
                }
            } else if (readNum == 0) {
                remote2LocalBuff.clear();
            } else if (readNum == -1) {
                remoteChannel.close();
                remoteSelectionKey.cancel();
                //下面这两个置为null挺关键的
                //将remoteChannal置为null，这样当浏览刷新是就会新建一个到远端的channal
                //而不是复用这个已经close的channal
                //也就避免了产生channel已经close的异常，也保证了刷新的成功。
                remoteChannel = null;
                remoteSelectionKey = null;
                remote2LocalBuff.clear();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            remote2LocalBuff.clear();
        }
    }

    private byte[] getRequestBody(byte[] request, int numRead) {
        int requestLength = numRead;
        for (int i = 0; i < requestLength; i++) {
            if (request[i] == 0) {
                requestLength = i;
                break;
            }
        }

        int bodyStartIndex = -1;
        for (int i = 0; i < requestLength - 3 && request[i] != 0; i++) {
            if (request[i] == 13 && request[i + 1] == 10 && request[i + 2] == 13 && request[i + 3] == 10) {
                bodyStartIndex = i + 4;
                break;
            }
        }
        if (bodyStartIndex != -1 && requestLength != bodyStartIndex) {
            int bodyLength = requestLength - bodyStartIndex;
            byte[] body = new byte[bodyLength];
            System.arraycopy(request, bodyStartIndex, body, 0, bodyLength);
            return body;
        } else {
            return null;
        }
    }

    public SocketChannel getRemoteChannal() {
        return remoteChannel;
    }

    public void setLocalSelectionKey(SelectionKey selectionKey) {
        this.localSelectionKey = selectionKey;
    }

    public void setRemoteSelectionKey(SelectionKey selectionKey) {
        this.remoteSelectionKey = selectionKey;
    }
}
