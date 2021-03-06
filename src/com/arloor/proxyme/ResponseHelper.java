package com.arloor.proxyme;

public class ResponseHelper {

    public static byte[] http503Error(){
        StringBuilder sb=new StringBuilder();
        sb.append("HTTP/1.0 503 Error\r\n");
        sb.append("Content-Language: zh-CN\r\n");
        sb.append("Content-Length: 649\r\n");
        sb.append("text/html;charset=ISO-8859-1\r\n");
        sb.append("\r\n");
        sb.append("<html>\n" +
                "<head>\n" +
                "    <title>Proxme Error Report</title>\n" +
                "    <style type=\"text/css\">\n" +
                "body {\n" +
                "    font-family: Arial,Helvetica,Sans-serif;\n" +
                "    font-size: 12px;\n" +
                "    color: #333333;\n" +
                "    background-color: #ffffff;\n" +
                "}\n" +
                "\n" +
                "h1 {\n" +
                "    font-size: 24px;\n" +
                "    font-weight: bold;\n" +
                "}\n" +
                "\n" +
                "h2 {\n" +
                "    font-size: 18px;\n" +
                "    font-weight: bold;\n" +
                "}\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "\n" +
                "<h1>Proxyme Error Report</h1>\n" +
                "<h2>Proxyme cannot process the request</h2>\n" +
                "<p>Proxyme failed to resolve the name of the remote host into an IP address. Check that the URL is correct.</p>\n" +
                "\n" +
                "<p>\n" +
                "<i>Proxyme, <a href=\"https://github.com/arloor/proxyme\">https://github.com/arloor/proxyme</a></i>\n" +
                "</p>\n" +
                "</body>\n" +
                "</html>");
        return sb.toString().getBytes();
    }
    public static byte[] httpsTunnelEstablished(){
        StringBuilder sb=new StringBuilder();
        sb.append("HTTP/1.1 200 Connection Established\r\n");
        sb.append("Proxy-agent: https://github.com/arloor/proxyme\r\n");
        sb.append("\r\n");
        return sb.toString().getBytes();
    }
}
