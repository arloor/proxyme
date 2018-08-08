# proxyme 一个http代理

使用java NIO的http代理。目前仅支持http，不支持https（不支持隧道）。

之前也打算做过这个东西，结果做出来的有点缺陷（现在想可能是selector中锁的问题，忘记了）。这大概隔了半年，这个项目的http代理功能实现了。



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

另外还有，因为代理而需要改变的请求头了，参见`RequestHeader.reform()`方法。

http代理不神秘。

## 问题

有个致命的问题：如果输了一个不存在的host，需要大概25s才能知道这个host无法解析。这一段时间，25s都无法处理浏览器的请求。

这就带来新的任务啦。读写channel可以用线程池搞。多弄几个线程用于读写。这也不是难事。