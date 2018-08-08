package com.arloor.proxyme;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class RequestHeader {
    private String method;
    private String path;
    private String protocol;
    
    private String host;
    private int port;

    public class Header{
        String key;
        String value;

        public Header(String key, String value) {
            this.key=key;
            this.value=value;
        }
    }

    private List<Header> headers=new ArrayList<>();

    public RequestHeader(String method, String path, String protocol) {
        this.method = method;
        this.path = path;
        this.protocol = protocol;
    }

    public void setHeader(String key,String value){
        for (int i = 0; i <headers.size() ; i++) {
            if(headers.get(i).key.equals(key)){
                headers.get(i).value=value;
                return;
            }
        }
        headers.add(new Header(key,value));
    }

    public String getHeader(String key){
        for (int i = 0; i <headers.size() ; i++) {
            if(headers.get(i).key.equals(key)){
                return headers.get(i).value;
            }
        }
        return null;
    }

    public void setPath(String path) {
        this.path=path;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    private void replaceKey(String oldKey,String newKey) {
        for (Header header:headers
                ) {
            if(header.key.equals(oldKey)){
                header.key=newKey;
                break;
            }
        }
    }

    public void reform(){
        //处理因为代理而发生的http请求头变化
       
        try {
            URL targetURL = new URL(path);
            this.host=targetURL.getHost();
            this.port=targetURL.getPort()!=-1?targetURL.getPort():targetURL.getDefaultPort();
            StringBuilder sb=new StringBuilder(targetURL.getPath());
            if(targetURL.getQuery()!=null){
                sb.append("?");
                sb.append(targetURL.getQuery());
            }
            this.setPath(sb.toString());
            if(this.getHeader("Host")==null){
                this.addHost(host);
            }
            replaceKey("Proxy-Connection","Connection");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    private void addHost(String host) {
        headers.add(0,new Header("Host",host));
    }

    public byte[] toBytes(){
        StringBuilder sb=new StringBuilder();
        sb.append(method+" "+ path+" "+ protocol+"\r\n");
        for (Header header:headers
             ) {
            sb.append(header.key+":"+ header.value+"\r\n");
        }
        sb.append("\r\n");
        return sb.toString().getBytes();
    }
}
