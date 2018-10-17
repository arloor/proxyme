# proxyme 一个http代理

使用java NIO的http代理。支持https。

因为墙的原因，这个代理不会处理域名中有`google`、`youtube`、`facebook`的请求。

之前也打算做过这个东西，结果做出来的有点缺陷（现在想可能是selector中锁的问题，忘记了）。这大概隔了半年，这个项目的http代理功能实现了。

## 运行日志

```
11:23:08.883 [main] INFO com.arloor.proxyme.HttpProxyBootStrap - 在8080端口启动了代理服务
11:23:12.208 [localSelector] INFO com.arloor.proxyme.LocalSelector - 接收浏览器连接: /127.0.0.1:50317
11:23:12.210 [localSelector] INFO com.arloor.proxyme.ChannalBridge - 请求—— CONNECT cn.bing.com:443 HTTP/1.1
11:23:12.291 [localSelector] INFO com.arloor.proxyme.ChannalBridge - 创建远程连接: cn.bing.com/202.89.233.100:443
11:23:12.291 [remoteSlector] INFO com.arloor.proxyme.RemoteSelector - 注册remoteChannel到remoteSelector。remoteChannel: cn.bing.com/202.89.233.100:443
11:23:12.298 [localSelector] INFO com.arloor.proxyme.ChannalBridge - 发送请求517 -->cn.bing.com/202.89.233.100:443
11:23:12.365 [remoteSlector] INFO com.arloor.proxyme.ChannalBridge - 接收响应2720 <--cn.bing.com/202.89.233.100:443
11:23:12.365 [remoteSlector] INFO com.arloor.proxyme.ChannalBridge - 接收响应1360 <--cn.bing.com/202.89.233.100:443
11:23:12.366 [remoteSlector] INFO com.arloor.proxyme.ChannalBridge - 接收响应1360 <--cn.bing.com/202.89.233.100:443
11:23:12.369 [remoteSlector] INFO com.arloor.proxyme.ChannalBridge - 接收响应1360 <--cn.bing.com/202.89.233.100:443
11:23:12.369 [remoteSlector] INFO com.arloor.proxyme.ChannalBridge - 接收响应1 <--cn.bing.com/202.89.233.100:443
11:23:12.378 [localSelector] INFO com.arloor.proxyme.ChannalBridge - 发送请求93 -->cn.bing.com/202.89.233.100:443
11:23:12.382 [localSelector] INFO com.arloor.proxyme.ChannalBridge - 发送请求1022 -->cn.bing.com/202.89.233.100:443
...
...
11:23:13.281 [localSelector] INFO com.arloor.proxyme.LocalSelector - 接收浏览器连接: /127.0.0.1:50319
11:23:13.282 [localSelector] INFO com.arloor.proxyme.ChannalBridge - 请求—— GET http://s.cn.bing.net/th?id=OSA.xiipvhS2Pp2bEg&w=80&h=80&c=8&rs=1&pid=SatAns HTTP/1.1
11:23:13.382 [localSelector] INFO com.arloor.proxyme.ChannalBridge - 创建远程连接: s.cn.bing.net/112.84.133.11:80
11:23:13.383 [remoteSlector] INFO com.arloor.proxyme.RemoteSelector - 注册remoteChannel到remoteSelector。remoteChannel: s.cn.bing.net/112.84.133.11:80
11:23:13.383 [localSelector] INFO com.arloor.proxyme.ChannalBridge - 发送请求340 -->s.cn.bing.net/112.84.133.11:80
11:23:13.383 [localSelector] INFO com.arloor.proxyme.ChannalBridge - 发送请求409 -->s.cn.bing.net/112.84.133.11:80
```

## 性能与内存

占用cpu不到1%

内存最大35m（不含jvm自身）。GC次数和时间很少

总的来说，性能可以了吧。

## 思路

两个线程，每个线程一个selector。

localSelector线程，负责接收本地浏览器的连接请求和读写浏览器到代理的socketChannel

remoteSelector线程，负责读写web服务器到代理的socketChannel。

ChannelBridge类,持有localSocketChannel和remoteSocketChannel。职责是处理请求和响应，并转发。

RequestHeader类，职责是格式化请求行和请求头。

## 实现中的注意点

首先是健壮性！每一个try块都是很重要的！都解决了一个问题

其次是锁的问题：

selector.select()会占有锁，channel.register(selector)需要持有同样的锁。

如果调用上面的两个方法的语句在两个线程中，会让channel.regiter等很久很久，导致响应难以及时得到。

而在实现中，这是一个生产者消费者问题。localSelector线程根据本地浏览器请求产生了一个从代理到web服务器的remoteChannel。而remoteSelector要接收这个remoteChannel,这也就是消费了。

很自然的，避免上面锁等待最好的方法：localSelector生成remoteChannel，将其放入队列。remoteSelector线程从队列中取。再结合selector.wakeup()使其从阻塞中返回，可以快速地接收（register）这个remoteChannel。

这两点，就是最最重要的两点了。

另外还有，因为代理而需要改变的请求头了，参见`com.arloor.proxyme.RequestHeader.reform()`方法。

最后，https代理实现中的坑。http代理传输的内容是明文，字节肯定大于0，而https传输的字节可能小于0。因为这个，传输https数据的bybebuff时，要特意指定bytebuff的limit为实际大小。

还有一个小问题，向remoteChannel 写的时候，有时候会写0个字节，原因是底层tcp缓冲满了，我的处理是等0.1秒，再继续传。当然设置OP_WRITE这个监听选项的目的就是处理这种情况。

http代理不神秘。

## 命令行参数

可以添加两个命令行参数： `host=xxxx port=8080`

如果不设置这两个参数： 本地代理将使用`InetAddress.getLocalhost()`的ip和`8080`的端口。

注意，一台电脑会有好几个ip地址。如果使用127.0.0.1，则该代理只可以在本机上使用。如果使用局域网地址（最常见的是192.168.x.x），并合理配置jdk的防火墙权限，则可以在局域网中使用该代理。

那么如何把该代理部署到云服务器上？如下：

```
[root@VM_26_36_centos ~]ifconfig
eth0: flags=4163<UP,BROADCAST,RUNNING,MULTICAST>  mtu 1500
        inet 10.154.26.36  netmask 255.255.192.0  broadcast 10.154.63.255
        ether 52:54:00:b5:bb:6a  txqueuelen 1000  (Ethernet)
        RX packets 41677295  bytes 5458697312 (5.0 GiB)
        RX errors 0  dropped 0  overruns 0  frame 0
        TX packets 41351236  bytes 5660742157 (5.2 GiB)
        TX errors 0  dropped 0 overruns 0  carrier 0  collisions 0

lo: flags=73<UP,LOOPBACK,RUNNING>  mtu 65536
        inet 127.0.0.1  netmask 255.0.0.0
        loop  txqueuelen 1000  (Local Loopback)
        RX packets 3370828  bytes 195574819 (186.5 MiB)
        RX errors 0  dropped 0  overruns 0  frame 0
        TX packets 3370828  bytes 195574819 (186.5 MiB)
        TX errors 0  dropped 0 overruns 0  carrier 0  collisions 0

[root@VM_26_36_centos ~]# java -jar proxyme.jar host=10.154.26.36
15:09:03.245 [main] WARN HttpProxyBootStrap - 提示：允许携带两个命令行参数 host=xxx port=1234
15:09:03.337 [main] INFO LocalSelector - 在10.154.26.36:8080端口启动了代理服务。注意可能非127.0.0.1
```

先ifconfig找到自己的云服务器内网地址（其实和局域网地址一个意思），然后在启动的命令行参数中，增加`host=内网地址`，进行启动。当然还需要配置防火墙。配好之后就可以连上服务器使用代理了。

不过，不可以用来翻墙。。

## 可以改进的地方

对channel的读写可以加入更多的线程来进行。

