## 产生场景

> 在我做的一个模块中，会用到遍历一个集合类，遍历的同时根据条件判断集合中的对象，如果不符合条件则将该对象从集合中移除。这种情况很容易产生`ConcurrentModificationExceptionException`，这个异常会导致程序停止继续运行，所以遇到这个异常必须要处理来保证程序正确运行。

### 关于ConcurrentModificationException

`ConcurrentModificationException` 这个异常是从JDK1.2时就存在。当方法检测到对象的并发修改，但不允许这种修改时，抛出此异常。这个异常在单线程和多线程运行环境都可以产生。

某个线程在 `Collection` 上进行迭代时，通常不允许另一个线性修改该`Collection`。通常在这些情况下，迭代的结果是不确定的。如果检测到这种行为，一些迭代器可能选择抛出此异常。

执行该操作的迭代器称为快速失败迭代器，因为迭代器很快就完全失败，而不会冒着在将来某个时间任意发生不确定行为的风险。

## 单线程触发场景举例

### 1. 单线程触发举例
```java
public class Demo1 {

    public static void main(String[] args) {
        /** 初始化集合类*/
        ArrayList<TestObj> list = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            list.add(new TestObj(i));
        }

        /** 遍历时删除元素*/
        for (TestObj obj : list) {
            if (obj.getValue() < 10) {
                /** 这里会抛出ConcurrentModificationException*/
                list.remove(obj);
            }
        }
    }
}
```

### 解决单线程环境的ConcurrentModificationException异常

单线程环境中可以通过将`ArrayLis`t集合改为`CopyOnWriteArrayList`，或者可以通过迭代器遍历删除，可以避免出现`ConcurrentModificationException`异常.

通过集合类的`iterator()`方法获取迭代器对象`Iterator` ,通过迭代器对象的`iterator.hasNext()`方法判断是否还有数据，如果有的话，通过iterator.next()方法得到下一个对象，然后通过`iterator.remove`()方法删除.在单线程环境中这样可以避免出现`ConcurrentModificationException`。
```java
public class Demo2 {

    public static void main(String[] args) {
        for (int n = 0; n < 1000000; n++) {
            /** 初始化集合类*/
            ArrayList<TestObj> list = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                list.add(new TestObj(i));
            }

            /** 遍历时删除元素*/
            Iterator<TestObj> iterator = list.iterator();
            while (iterator.hasNext()) {
                TestObj testObj = iterator.next();
                if (testObj.getValue() < 10) {
                    iterator.remove();
                }
            }
        }
    }
}
```

### 这个异常的原理是：
集合中有一个版本号，每次修改（添加或删除）集合的时候，版本号都会往上涨。当开始遍历集合的时候，先记录下这个版本号，在遍历期间，如果发现这个版本号和集合当前的版本号不等，就会抛这个异常。

而Itr自己对集合对象进行了修改后，他会维持`expectedModCount` 和`modCount`的保持相等。
在我们调用集合对象的`iterator()`方法的`remove`时总会使list的modCount的值自增1，但是Itr会自己维护该值和`expectedModCount` 的一致。

经分析发现抛出`ConcurrentModificationException`异常处于调用next()方法时，比较`expectedModCount` 和`modCount`的值，如果两个值不相等，就会抛出异常，然而在什么情况下会使`expectedModCount` 和`modCount`的值不相等呢，只有在两个Itr同时对一个list进行操作的时候才会出现这样的问题，所以在以后的编码过程中在是由Iterator进行remove()时一定要考虑是否时多线程的，如果是请用`terator`进行`remove()`，而不要使用`List`的`remove`方法进行。

## 2. 多线程触发举例
```java
public class ListConcurrentTest {
    private static final int THREAD_POOL_MAX_NUM = 10;
    private List<String> mList = new ArrayList<String>();

    public static void main(String args[]) {
        new ListConcurrentTest().start();
    }

    private void initData() {
        for (int i = 0; i <= THREAD_POOL_MAX_NUM; i++) {
            this.mList.add("...... Line " + (i + 1) + " ......");
        }
    }

    private void start() {
        initData();
        ExecutorService service = Executors.newFixedThreadPool(THREAD_POOL_MAX_NUM);
        for (int i = 0; i < THREAD_POOL_MAX_NUM; i++) {
            service.execute(new ListReader(this.mList));
            service.execute(new ListWriter(this.mList, i));
        }
        service.shutdown();
    }

    private class ListReader implements Runnable {
        private List<String> mList;

        public ListReader(List<String> list) {
            this.mList = list;
        }

        @Override
        public void run() {
            if (this.mList != null) {
                for (String str : this.mList) {
                    System.out.println(Thread.currentThread().getName() + " : " + str);
                }
            }
        }
    }

    private class ListWriter implements Runnable {
        private List<String> mList;
        private int mIndex;

        public ListWriter(List<String> list, int index) {
            this.mList = list;
            this.mIndex = index;
        }

        @Override
        public void run() {
            if (this.mList != null) {
                //this.mList.remove(this.mIndex);
                this.mList.add("...... add " + mIndex + " ......");
            }
        }
    }
}
```

所以这里最大的问题，在同一时间多个线程无法对同一个`List`进行读取和增删，否则就会抛出并发异常。

将`ArrayList`改为`CopyOnWriteArrayList`在多线程环境中同样可以避免出现这个异常。

### 现在来说说CopyOnWriteArrayList的原理和使用方法

> `CopyOnWriteArrayList`这是一个`ArrayList`的线程安全的变体，其原理大概可以通俗的理解为: 初始化的时候只有一个容器，很常一段时间，这个容器数据、数量等没有发生变化的时候，大家(多个线程)，都是读取(假设这段时间里只发生读取的操作)同一个容器中的数据，所以这样大家读到的数据都是唯一、一致、安全的，但是后来有人往里面增加了一个数据，这个时候`CopyOnWriteArrayList` 底层实现添加的原理是先copy出一个容器(可以简称副本)，再往新的容器里添加这个新的数据，最后把新的容器的引用地址赋值给了之前那个旧的的容器地址，但是在添加这个数据的期间，其他线程如果要去读取数据，仍然是读取到旧的容器里的数据。

### 原理:

无论我们用哪一个构造方法创建一个`CopyOnWriteArrayList`对象，

都会创建一个`Object`类型的数组，然后赋值给成员array。

> private transient volatile Object[] array;

**提示:** `transient`关键字主要启用的作用是当这个对象要被序列化的时候，不要将被`transient`声明的变量(Object[] array)序列化到本地。

要看`CopyOnWriteArrayList`怎么处理并发的问题，当然要去了解它的增、删、修改、读取方法是怎么处理的了。现在我们直接来看看:
```java
public boolean add(E e) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            Object[] newElements = Arrays.copyOf(elements, len + 1);
            newElements[len] = e;
            setArray(newElements);
            return true;
        } finally {
            lock.unlock();
        }
    }
```

```java
final ReentrantLock lock = this.lock;
lock.lock();
```

首先使用上面的两行代码加上了锁，保证同一时间只能有一个线程在添加元素。

然后使用`Arrays.copyOf(...)`方法复制出另一个新的数组，而且新的数组的长度比原来数组的长度+1，副本复制完毕，新添加的元素也赋值添加完毕，最后又把新的副本数组赋值给了旧的数组，最后在`finally`语句块中将锁释放。
```java
public E remove(int index) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            E oldValue = get(elements, index);
            int numMoved = len - index - 1;
            if (numMoved == 0)
                setArray(Arrays.copyOf(elements, len - 1));
            else {
                Object[] newElements = new Object[len - 1];
                System.arraycopy(elements, 0, newElements, 0, index);
                System.arraycopy(elements, index + 1, newElements, index,
                                 numMoved);
                setArray(newElements);
            }
            return oldValue;
        } finally {
            lock.unlock();
        }
    }
```

然后我们再来看一个`remove`，删除元素，很简单，就是判断要删除的元素是否最后一个，如果最后一个直接在复制副本数组的时候，复制长度为旧数组的length-1即可；
但是如果不是最后一个元素，就先复制旧的数组的index前面元素到新数组中，然后再复制旧数组中index后面的元素到数组中，最后再把新数组复制给旧数组的引用。


最后在`finally`语句块中将锁释放。


其他的一些重载的增删、修改方法其实都是一样的逻辑，这里就不重复讲解了。

最后我们再来看一个读取操作的方法:
```java
 public E get(int index) {
        return get(getArray(), index);
    }
```
以我们可以看到，其实读取的时候是没有加锁的。

## CopyOnWriteArrayList的优点和缺点:
### 优点:
     1.解决的开发工作中的多线程的并发问题。

### 缺点:
     1.内存占有问题:很明显，两个数组同时驻扎在内存中，如果实际应用中，数据比较多，而且比较大的情况下，占用内存会比较大，针对这个其实可以用ConcurrentHashMap来代替。
     2.数据一致性:CopyOnWrite容器只能保证数据的最终一致性，不能保证数据的实时一致性。所以如果你希望写入的的数据，马上能读到，请不要使用CopyOnWrite容器
