public class HttpProxyBootStrap {

    public static void main(String[] args) {
        LocalSelector localSelector =LocalSelector.getInstance();
        Thread localSelectorThead=new Thread(localSelector,"localSelector");
        localSelectorThead.start();

        RemoteSelector remoteSelector=RemoteSelector.getInstance();
        Thread remoteSelectorThread=new Thread(remoteSelector,"remoteSlector");
        remoteSelectorThread.start();
    }
}
