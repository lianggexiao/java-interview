## ThreadLocal用在什么地方？

讨论`ThreadLocal`用在什么地方前，我们先明确下，如果仅仅就一个线程，那么都不用谈`ThreadLocal`的，`ThreadLocal`是用在多线程的场景的！
`ThreadLocal`归纳下来就2类用途：

- 保存线程上下文信息，在任意需要的地方可以获取；

- 线程安全的，避免某些情况需要考虑线程安全，因而必须同步，带来的性能损失。

### 保存线程上下文信息，在任意需要的地方可以获取。

由于`ThreadLocal`的这个特性，同一线程在某地方进行设置，在随后的任意地方都可以获取到。从而可以用来保存线程上下文信息。

常用的日志打印，比如每个请求怎么把一串后续关联起来，就可以用`ThreadLocal`进行set，在后续的任意需要记录日志的方法里面进行get获取到请求id，从而把整个请求串起来。

还有比如Spring的事务管理，用`ThreadLocal`存储Connection，从而各个DAO可以获取同一Connection，可以进行事务回滚，提交等操作。

线程安全的，避免某些情况需要考虑线程安全必须同步带来的性能损失。

`ThreadLocal`为解决多线程程序的并发问题提供了一种新的思路。但是ThreadLocal也有局限性，每个线程往`ThreadLocal`中读写数据是线程隔离，互相之间不会影响的，所以`ThreadLocal`无法解决共享对象的更新问题！由于不需要共享信息，自然就不存在竞争问题了，从而保证了某些情况下线程的安全，以及避免了某些情况需要考虑线程安全必须同步带来的性能损失！！！

这类场景阿里规范里面也提到了，如图：
![](https://github.com/lianggexiao/java-interview/blob/master/img/threadLocal1.jpg)

应用举例：
```java
public class ThreadLocalTest {
    private static ThreadLocal<Integer> threadLocal = new ThreadLocal<>();

    public static void main(String[] args) {

        new Thread(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    threadLocal.set(i);
                    System.out.println(Thread.currentThread().getName() + "====" + threadLocal.get());
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } finally {
                threadLocal.remove();
            }
        }, "threadLocal1").start();


        new Thread(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    System.out.println(Thread.currentThread().getName() + "====" + threadLocal.get());
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } finally {
                threadLocal.remove();
            }
        }, "threadLocal2").start();
    }
}
```
## 设计原理

首先 `ThreadLocal `是一个泛型类，保证可以接受任何类型的对象。

因为一个线程内可以存在多个` ThreadLocal` 对象，所以其实是 ThreadLocal 内部维护了一个 Map ，这个 Map 不是直接使用的 HashMap ，而是 `ThreadLocal `实现的一个叫做 ThreadLocalMap 的静态内部类。而我们使用的 get()、set() 方法其实都是调用了这个ThreadLocalMap类对应的 get()、set() 方法。

`Thread`类有属性变量threadLocals （类型是ThreadLocal.ThreadLocalMap），也就是说每个线程有一个自己的`ThreadLocalMap` ，所以每个线程往这个`ThreadLocal`中读写隔离的，并且是互相不会影响的。一个ThreadLocal只能存储一个Object对象，如果需要存储多个Object对象那么就需要多个ThreadLocal。

### java对象的引用包括 ：强引用，软引用，弱引用，虚引用 。

因为这里涉及到弱引用，简单说明下：

弱引用也是用来描述非必需对象的，当JVM进行垃圾回收时，无论内存是否充足，该对象仅仅被弱引用关联，那么就会被回收。

当仅仅只有`ThreadLocalMap`中的Entry的key指向`ThreadLocal`的时候，`ThreadLocal`会进行回收的！！！

`ThreadLocal`被垃圾回收后，在`ThreadLocalMap`里对应的Entry的键值会变成null，但是Entry是强引用，那么Entry里面存储的Object，并没有办法进行回收，所以`ThreadLocalMap` 做了一些额外的回收工作。

所以ThreadLocal最佳实践，应该在我们不使用的时候，主动调用remove方法进行清理。

如图：
![](https://github.com/lianggexiao/java-interview/blob/master/img/ThreadLocal2.jpg)

这里把ThreadLocal定义为static还有一个好处就是，由于`ThreadLocal`有强引用在，那么在ThreadLocalMap里对应的Entry的键会永远存在，那么执行remove的时候就可以正确进行定位到并且删除！！！



