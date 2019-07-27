## ReentrantLock 实现原理
`ReentrantLock` 就是一个普通的类，它是基于 `AQS`(AbstractQueuedSynchronizer)来实现的。是一个可重入锁：一个线程获得了锁之后仍然可以反复的加锁，不会出现自己阻塞自己的情况。

`AQS` 是 Java 并发包里实现锁、同步的一个重要的基础框架。

### 可重入性的原理

synchronized在不同的锁级别下， 重入的实现不一样。

- 偏向锁： `Mark Word` 中记录的ThreadId。
- 轻量级锁：`Mark Word` 指向的当前线程的锁记录。
- 重量级锁：采用`Mark Word` 指向对比和_recursions累增加锁、减锁。

`ReentrantLock` 基于当前锁持有的线程对象thread， 然后对 state 进行累计或者累减。和重量级锁的实现比较像。

### 锁类型
`ReentrantLock` 分为公平锁和非公平锁，可以通过构造方法来指定具体类型：
```java
    //默认非公平锁
    public ReentrantLock() {
        sync = new NonfairSync();
    }
    
    //公平锁
    public ReentrantLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
    }
```
默认一般使用非公平锁，它的效率和吞吐量都比公平锁高的多(后面会分析具体原因)。

### 公平锁：

公平和非公平锁的队列都基于锁内部维护的一个双向链表，表结点Node的值就是每一个请求当前锁的线程。公平锁则在于每次都是依次从队首取值。
公平锁就是每个线程在获取锁时会先查看此锁维护的等待队列，如果为空，或者当前线程线程是等待队列的第一个，就占有锁，否则就会加入到等待队列中，以后会按照FIFO的规则从队列中获取。

公平锁与非公平锁主要是`tryAcquire`方法实现方式不一样。`NonfairSync`继承它(AQS)来实现非公平锁，`FairSync`继承它(AQS)来实现公平锁，AQS提供一个tryAcquire()的模板方法来使得公平锁和非公平锁的实现方式显得灵活。(用到了模版模式)

### 锁的实现方式是基于如下几点：

    - 表结点Node和状态state的`volatile`关键字。
    - sum.misc.Unsafe.compareAndSet的原子操作(见附录)。

非公平锁： 在等待锁的过程中， 如果有任意新的线程妄图获取锁，都是有很大的几率直接获取到锁的。
首先获取当前线程，和当前`AQS`维护的锁的状态，如果状态为0，则尝试将AQS的status从0设为acquires(实际是1)，如果设置成功，则获取锁成功，把当前锁设置为锁的持有者，返回true；如果当前线程已经是锁的持有者，则把status+acquires，如果结果越界，抛出异常，如果成功，返回true。


### 获取锁
通常的使用方式如下:
```java
    private ReentrantLock lock = new ReentrantLock();
    public void run() {
        lock.lock();
        try {
            //do bussiness
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }
```

### CAS和volatile， Java并发的基石
公平锁和非公平锁在说的获取上都使用到了 `volatile` 关键字修饰的`state`字段， 这是保证多线程环境下锁的获取与否的核心。
但是当并发情况下多个线程都读取到 state == 0时，则必须用到CAS技术，一门CPU的原子锁技术，可通过CPU对共享变量加锁的形式，实现数据变更的原子操作。
`volatile` 和 `CAS`的结合是并发抢占的关键。


`CAS`是CPU提供的一门技术。在单核单线程处理器上，所有的指令允许都是顺序操作；但是在多核多线程处理器上，多线程访问同一个共享变量的时候，可能存在并发问题。
使用CAS技术可以锁定住元素的值。
编译器在将线程持有的值与被锁定的值进行比较，相同则更新为更新的值。
`CAS`同样遵循JMM规范的 `happen-before` 原则。


## 总结
由于公平锁需要关心队列的情况，得按照队列里的先后顺序来获取锁(会造成大量的线程上下文切换)，而非公平锁则没有这个限制。
所以也就能解释非公平锁的效率会被公平锁更高。


