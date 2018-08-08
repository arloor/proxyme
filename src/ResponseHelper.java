public class ResponseHelper {

    public static byte[] http404(){
        StringBuilder sb=new StringBuilder();
        sb.append("HTTP/1.1 404 HostUnValid\r\n");
        sb.append("Content-Language: zh-CN\r\n");
        sb.append("Content-Length: 25\r\n");
        sb.append("Content-Type: text/plain;charset=ISO-8859-1\r\n");
        sb.append("\r\n");
        sb.append("the url is unvalid! -_-!\n");
        return sb.toString().getBytes();
    }
}
