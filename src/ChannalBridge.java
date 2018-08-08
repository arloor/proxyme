import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.util.regex.Pattern;

public class ChannalBridge {
    private SocketChannel localChannel;
    private SocketChannel remoteChannel;
    private final ByteBuffer localBuff=ByteBuffer.allocate(8096);
    private final ByteBuffer remoteBuff=ByteBuffer.allocate(8096);
    private SelectionKey localSelectionKey;
    private SelectionKey remoteSelectionKey;

    private static Logger logger=LoggerFactory.getLogger(ChannalBridge.class);

    public ChannalBridge(SocketChannel localChannel) {
        this.localChannel = localChannel;
    }

    public void readLocal() {
        try {
            int numRead=0;
            try {
                numRead=localChannel.read(localBuff);
            }catch (IOException e){
                //todo:应该是出现了"你的主机中的软件中止了一个已建立的连接。"的异常  研究为什么会出现这个问题
                //一篇博文：https://blog.csdn.net/abc_key/article/details/29295569
                logger.warn("selector通知localChannel可读，但在读的过程中抛出异常。大概率异常信息为：“你的主机中的软件中止了一个已建立的连接。”。可以认为是这个localChannel出现异常，下面关闭这个localChannel。");
                logger.warn("异常信息: "+e.getMessage()+"  是不是上面说的？");
                localSelectionKey.cancel();
                localChannel.close();
            }

            if(numRead>0){
                readLocalAndSendToRemote();
            }else if(numRead==0){
                remoteBuff.clear();
            }else if(numRead==-1){
                localChannel.close();
                localSelectionKey.cancel();
                remoteBuff.clear();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void readLocalAndSendToRemote() throws IOException {
        byte[] readBytes=localBuff.array();
        localBuff.clear();

        ByteArrayInputStream byteArrayInputStream=new ByteArrayInputStream(readBytes);
        BufferedReader br=new BufferedReader(new InputStreamReader(byteArrayInputStream));
        String line=br.readLine();
        //如果是request line 例如： GET http://detectportal.firefox.com/success.txt HTTP/1.1
        if(Pattern.matches(".* .* HTTP.*",line)){

            String[] requestLineCells=line.split(" ");
            String method=requestLineCells[0];
            String urlstr=requestLineCells[1];
            String protocal=requestLineCells[2];
            logger.info("请求—— "+urlstr);
            RequestHeader requestHeader=new RequestHeader(method,urlstr,protocal);
            while((line=br.readLine())!=null&&line.length()!=0){
//                    System.out.println(line);
                String[] key_value=line.split(":");
                requestHeader.setHeader(key_value[0],key_value[1]);
            }
            requestHeader.reform();

            //创建remoteChannel
            if(remoteChannel==null){
                try {
                    remoteChannel=SocketChannel.open(new InetSocketAddress(requestHeader.getHost(),requestHeader.getPort()));
                }catch (Exception e){
                    //健壮性：如果无法连接远程服务器，不做处理这个线程会退出
                    logger.warn("连接到web服务器失败，返回404响应，关闭localChannel");
                    //给浏览器一个404的返回。如果不返回。。它会等很久。。并且还会重试
                    byte[] response404=ResponseHelper.http404();
                    localBuff.put(response404);
                    localBuff.flip();
                    localChannel.write(localBuff);
                    localBuff.clear();
                    localChannel.close();
                    localSelectionKey.cancel();
                    return;
                }
                remoteChannel.configureBlocking(false);
                logger.info("创建远程连接: " + remoteChannel.getRemoteAddress());
                RemoteSelector remoteSelector=RemoteSelector.getInstance();
                remoteSelector.addWaitRegistBridge(this);
            }
            byte[] requestHeaderBytes=requestHeader.toBytes();
//                System.out.print(new String(requestHeaderBytes));
            remoteBuff.put(requestHeaderBytes);
            remoteBuff.flip();//flip很关键 不flip 408错误
            try {
                remoteChannel.write(remoteBuff);
            }catch (ClosedChannelException e){
                logger.warn("试图写已关闭的remoteChannel："+e.getMessage()+"——已经解决了这个问题，就是remoteChannel close之后的=null。");
                //保险起见，但应该不会执行这四句
                remoteChannel.close();
                remoteSelectionKey.cancel();
                remoteChannel=null;
                remoteSelectionKey=null;
            }catch (IOException e){
                logger.warn("大概率出现了如下异常: 远程主机强迫关闭了一个现有的连接。看来是远程服务器的socket被异常关闭了。下面关闭这个远程socketChannal");
                logger.warn("异常信息： "+e.getMessage()+" 是不是上面说的？");
                remoteChannel.close();
                remoteSelectionKey.cancel();
                remoteChannel=null;
                remoteSelectionKey=null;
            }
            remoteBuff.clear();
            byte[] body=getRequestBody(readBytes);
            if(body!=null){
                remoteBuff.put(body);
                remoteBuff.flip();
                remoteChannel.write(remoteBuff);
                remoteBuff.clear();
            }
        }
    }

    public void readRemoteAndSendToLocal() {
        try {
            int readNum=0;
            try {
                readNum=remoteChannel.read(remoteBuff);
            }catch (IOException  e){
                //todo:应该是出现了"远程主机强迫关闭了一个现有的连接。"的异常 研究为什么会出现这个问题
                //一篇博文：https://blog.csdn.net/abc_key/article/details/29295569
                logger.warn("selector通知remoteChannel可读，但在读的过程中抛出异常。大概率异常信息为：“远程主机强迫关闭了一个现有的连接。”。可以认为是这个remoteChannel出现异常，下面关闭这个remoteChannel。");
                logger.warn("异常信息: "+e.getMessage()+"  是不是上面说的？");
                remoteSelectionKey.cancel();
                remoteChannel.close();
                remoteChannel=null;
                remoteSelectionKey=null;
            }
            if(readNum>0){
                logger.info("接收响应 "+remoteChannel.getRemoteAddress());
                remoteBuff.flip();
                System.out.println(new String(remoteBuff.array()));
                try {
                    localChannel.write(remoteBuff);
                }catch (ClosedChannelException e){
                    logger.warn("过期的响应，本地channel已经关闭。清理过期的远程channel");
                    remoteChannel.close();
                    remoteSelectionKey.cancel();
                    remoteChannel=null;
                    remoteSelectionKey=null;
                }catch (IOException e){
                    logger.warn("大概率出现了如下异常: 你的主机中的软件中止了一个已建立的连接。看来是本地浏览器的socket被异常关闭了。下面关闭这个本地socketChannal");
                    logger.warn("异常信息： "+e.getMessage()+" 是不是上面说的？");
                    localChannel.close();
                    localSelectionKey.cancel();
                }

                remoteBuff.clear();
            }else if(readNum==0){
                remoteBuff.clear();
            }else if(readNum==-1){
                remoteChannel.close();
                remoteSelectionKey.cancel();
                //下面这两个置为null挺关键的
                //将remoteChannal置为null，这样当浏览刷新是就会新建一个到远端的channal
                //而不是复用这个已经close的channal
                //也就避免了产生channel已经close的异常，也保证了刷新的成功。
                remoteChannel=null;
                remoteSelectionKey=null;
                remoteBuff.clear();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private byte[] getRequestBody(byte[] request){
        int requestLength=request.length;
        for (int i = 0; i <request.length ; i++) {
            if(request[i]==0){
                requestLength=i;
                break;
            }
        }

        int bodyStartIndex=-1;
        for (int i = 0; i <requestLength-3 && request[i]!=0; i++) {
            if(request[i]==13&&request[i+1]==10&&request[i+2]==13&&request[i+3]==10){
                bodyStartIndex=i+4;
                break;
            }
        }
        if(bodyStartIndex!=-1&&requestLength!=bodyStartIndex){
            int bodyLength=requestLength-bodyStartIndex;
            byte[] body=new byte[bodyLength];
            System.arraycopy(request,bodyStartIndex,body,0,bodyLength);
            return body;
        }else{
            return null;
        }
    }

    public SocketChannel getRemoteChannal() {
        return remoteChannel;
    }

    public void setLocalSelectionKey(SelectionKey selectionKey) {
        this.localSelectionKey=selectionKey;
    }

    public void setRemoteSelectionKey(SelectionKey selectionKey) {
        this.remoteSelectionKey=selectionKey;
    }
}
