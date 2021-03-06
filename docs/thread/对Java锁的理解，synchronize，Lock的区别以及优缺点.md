# 对Java锁的理解，synchronize，Lock的区别以及优缺点

Java中为了提高程序性能，我们经常会使用到多线程。在多线程环境下，多个线程抢夺一个资源，不可避免会有线程安全问题。这时为了实现原子性操作，我们必须使用到锁。

Java中实现同步操作常见的方式有两种

1）使用同步关键字`synchronized`

2）使用`lock`锁机制

## synchronized 关键字原理
众所周知`synchronized` 关键字是解决并发问题常用解决方案，有以下三种使用方式:
- 同步普通方法，锁的是当前对象。
- 同步静态方法，锁的是当前 Class 对象。
- 同步块，锁的是 () 中的对象。

`实现原理`： JVM 是通过进入、退出对象监视器( Monitor )来实现对方法、同步块的同步的。

使用 `synchronized` 来做同步处理时，锁的获取和释放都是隐式的，实现的原理是通过编译后加上不同的机器指令来实现。

具体实现是在编译之后在同步方法调用前加入一个 monitor.enter 指令，在退出方法和异常处插入 monitor.exit 的指令。

其本质就是对一个对象监视器( Monitor )进行获取，而这个获取过程具有排他性从而达到了同一时刻只能一个线程访问的目的。

而对于没有获取到锁的线程将会阻塞到方法入口处，直到获取锁的线程 monitor.exit 之后才能尝试继续获取锁。

流程图如下:

![](https://ws2.sinaimg.cn/large/006tNc79ly1fn27fkl07jj31e80hyn0n.jpg)

使用 `javap -c Synchronize` 可以查看编译之后的具体信息。
可以看到在同步块的入口和出口分别有 `monitorenter`,`monitorexit` 指令。


## 锁优化
`synchronized` 很多都称之为重量锁，JDK1.6 中对 `synchronized` 进行了各种优化，为了能减少获取和释放锁带来的消耗引入了偏向锁和轻量锁。

### 偏向锁
为了进一步的降低获取锁的代价，JDK1.6 之后还引入了偏向锁。

当一个`synchronized`块或者对象锁方法执行时，不存在锁竞争，则获取偏向锁。

无锁竞争时，A线程获得锁，同时锁对象的对象头Mark Word里存储A线程的线程id。后续的重入则通过判断id进行。
偏向锁的释放，要么有线程竞争、要么代码块执行完毕。
偏向锁的释放，如果不存在竞争，将对象头设置成无锁状态（代码块执行完毕自动释放/线程完毕）；
如果存在竞争，释放对象头Mark Word锁信息；所有竞争线程转轻量级锁； 之前拥有偏向锁的栈会被执行。

偏向锁的特征是:锁不存在多线程竞争，并且应由一个线程多次获得锁。

当线程访问同步块时，会使用 CAS 将线程 ID 更新到锁对象的 `Mark Word`中，如果更新成功则获得偏向锁，并且之后每次进入这个对象锁相关的同步块时都不需要再次获取锁了。

### 轻量锁

在偏向锁升级为轻量级锁后。 竞争失败的锁可能采用自旋的方式， 在N次自旋中尝试获取锁，此时所有的竞争线程都平等。因此`synchronized`是非公平锁。

锁竞争的情况下，竞争的线程都会复制锁对象的`Mark Word`信息。
A线程获得轻量级锁，会在A线程的栈帧里创建`lock record`，让`lock record`的指针指向锁对象的对象头中的`Mark Word`， 同时让`Mark Word` 指向`lock record`。
同样通过对比指针信息， 来实现锁的重入。
轻量级锁，在于锁竞争失败的线程，首先不进入内核态，而是采用自旋，空循环的方式等待A线程释放锁。
当完成自旋策略还是发现没有释放锁，或者让其他线程占用了。则轻量级锁升级为重量级锁。

### 重量级锁
重量级锁耗费资源， 在于线程的挂起和用户态和内核态的切换。重量级锁处理逻辑也是一个抢占、挂起、唤醒的过程。
参考`monitorenter`、`monitorexit`在JVM中的实现

### 解锁
轻量锁的解锁过程也是利用 CAS 来实现的，会尝试锁记录替换回锁对象的 Mark Word 。如果替换成功则说明整个同步操作完成，失败则说明有其他线程尝试获取锁，这时就会唤醒被挂起的线程(此时已经膨胀为重量锁)

轻量锁能提升性能的原因是：

认为大多数锁在整个同步周期都不存在竞争，所以使用 CAS 比使用互斥开销更少。但如果锁竞争激烈，轻量锁就不但有互斥的开销，还有 CAS 的开销，甚至比重量锁更慢。

### 释放锁
当有另外一个线程获取这个锁时，持有偏向锁的线程就会释放锁，释放时会等待全局安全点(这一时刻没有字节码运行)，接着会暂停拥有偏向锁的线程，根据锁对象目前是否被锁来判定将对象头中的 Mark Word 设置为无锁或者是轻量锁状态。

偏向锁可以提高带有同步却没有竞争的程序性能，但如果程序中大多数锁都存在竞争时，那偏向锁就起不到太大作用。可以使用 -XX:-UseBiasedLocking 来关闭偏向锁，并默认进入轻量锁。

### 其他优化
适应性自旋
在使用 CAS 时，如果操作失败，CAS 会自旋再次尝试。由于自旋是需要消耗 CPU 资源的，所以如果长期自旋就白白浪费了 CPU。JDK1.6加入了适应性自旋:

如果某个锁自旋很少成功获得，那么下一次就会减少自旋。


## 区别
首先`synchronized`是java内置关键字，在jvm层面，Lock是`java.util.concurrent.locks`包下的接口，Lock 实现提供了比使用`synchronized` 方法和语句可获得的更广泛的锁定操作，它能以更优雅的方式处理线程同步问题。需要注意的是，用sychronized修饰的方法或者语句块在代码执行完之后锁自动释放，而用Lock需要我们手动释放锁，所以为了保证锁最终被释放(发生异常情况)，要把加锁放在try内，释放锁放在finally内。

`synchronized`无法判断是否获取锁的状态，`Lock`可以判断是否获取到锁；

用`synchronized`关键字的两个线程1和线程2，如果当前线程1获得锁，线程2线程等待。如果线程1阻塞，线程2则会一直等待下去，而Lock锁就不一定会等待下去，如果尝试获取不到锁，线程可以不用一直等待就结束了；

`synchronized`的锁可重入、不可中断、非公平，而Lock锁可重入、可中断、可公平（两者皆可）

