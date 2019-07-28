## 类加载的概述
### 双亲委派加载机制
> 双亲委派模型的工作流程是：如果一个类加载器收到了类加载的请求，它首先不会自己去尝试加载这个类，而是把请求委托给父加载器去完成，依次向上，因此，所有的类加载请求最终都应该被传递到顶层的启动类加载器中，只有当父加载器在它的搜索范围中没有找到所需的类时，即无法完成该加载，子加载器才会尝试自己去加载该类。

### 通俗的理解就是：


1. 遇见一个类需要加载的类，它会优先让父加载器去加载。层层传递。

2. 每个类加载器都有自己的加载区域，它也只能在自己的加载区域里面寻找。

3. 自定义类加载器也必须实现这样一个双亲委派模型。

4. 双亲委派机制是隔离的关键， 如String.class：

     - 一个JVM里面只能有一个String.class。

     - 用户没法自定义个String.class出来。

     - 每个`Classloader`都有自己的加载区域，需要注意部分配置文件的存放地点。

** 加载顺序 **
- `BootstrapClassLoader`加载${JAVA_HOME}/jre/lib 下面的部分jar包。比如java.*、sun.*

- `ExtClassLoader`加载${JAVA_HOME}/jre/lib/ext下面的jar包。比如javax.*

- `AppClassLoader`加载用户classpath下面的jar包。

- 如果自定义了`classloader`, 在符合双亲委派模型的基础上，它加载用户自定义classpath下的jar包， 例如tomcat的WEB-INF/class和WEB-INF/lib.

## 类加载的隔离机制

通过不同的 完整类名 和 classloader， 可以区分两个类。这样的好处是内存隔离(最常见的就是静态变量)。

类名不一致一定不是同一个类
类名一致类加载器不一致也不是同一个类(eaquels false)
类名一致类加载器一致但是类加载器实例不一致也不是同一个类。

### 案例:
在web应用中假如部署了多个webapp. 为了方便共享就预先在Tomcat lib里面内置了部分类比如Spring、JDBC。而用户自备也有类似的Jar包。 这样会引起什么样的冲突？

答案是不会冲突。

`Tomcat`提供了一个Child优先的类加载机制：首先由子类去加载， 加载不到再由父类加载。就很好的规避了这个问题。WEB-INF/lib 目录下的类的加载优先级是优于Tomcat lib的。（配置文件在`server.xml`里面的<Loader delegate ="false"/> default false）上。

针对`Tomcat`， 做一个加载路径的介绍：
- Tomcat起始于catalina.sh里面的命令 java org.apache.catalina.startup.Bootstrap start

- 因为显式的指定了java命令，因此

- `BootstrapClassLoader`负责加载${JAVA_HOME}/jre/lib部分jar包

- `ExtClassLoader`加载${JAVA_HOME}/jre/lib/ext下面的jar包

- `AppClassLoader`加载bootstrap.jar和tomcat-juli.jar （只显示的指定了这两个jar包）

- 之后Tomcat通过初始化了三个`URLClassLoader`, 并指定加载路径 （见catalina.properties#common.loader配置）

- 除了`common`外， server和shardLoader的加载路径一般都没有显示的指定， 因此这三个Loader实际上都是`URLClassLoader`。

- 同时，它顺便指定了当前线程的`contextClassLoader`。

- `Tomcat`对于WEB应用的启动都是依赖于web.xml的， 里面配置的Filter、Listener、Servlet根据Tomcat的定义都是由`WebappClassLoaderBase`来加载的。

- 毕竟`Filter`、`Listener`、`Servlet`等入口都是被`WebappClassLoaderBase`加载的，而一般开发者不会主动指定`ClassLoader`。那么除非指定了ClassLoader，所有的webapp都是它加载的（刚好它的加载空间包含了这些类）

- 在需要Spring的时候已经由App自身加载得到， 就不会再去寻找Tomcat lib里面的Spring。

- 自此，Tomcat的类加载区分完毕。 通过 “子优先” 这个机制，可以保证多个 Tomcat App 之间做到良好的隔离。

`contextClassLoader`
> Thread.currentThread().getContextClassLoader()一般有两个用处：给SPI用， 找配置文件用。

### SPI用处(后面会说何为SPI)
之前讲解过java的委托加载机制如图：
![类加载机制](https://github.com/lianggexiao/java-interview/tree/master/img/jvm_classload1.png)

UserClassLoader -> AppClassLoader->ExtClassLoader -> Bootstrap


委派链左边的`ClassLoader`就可以很自然的使用右边的`ClassLoader`所加载的类。


情况反过来，右边的`ClassLoader`所加载的代码需要反过来去找委派链靠左边的`ClassLoader`去加载东西怎么办呢？
没辙，双亲委托机制是单向的，没办法反过来从右边找左边。

### 解决方案：
> ServiceLoader.load(Class.class); 在加载类的时候， ServiceLoader由BootStrap加载，而一般的SPI都是在用户的classpath下。鉴于方法调用默认是使用的调用类的ClassLoader去加载， 显然BootStrap是加载不了没在它的路径下的Class的， 这个时候就可以传入一个Thread.currentThread().getContextClassLoader()， 就可以很轻松的找到资源文件.

### 找文件用处
这个跟上诉的SPI机制其实也差不多， 都是每个ClassLoader负责一定的区域， 如果当前区域找不到再使用线程的Loader去找。
比如在Tomcat中执行一个 new File(), 会不会发现文件到${catalina.home}/bin里面去了？

## 类加载的顺序

### 老生常谈：

1. 装载：查找和导入Class文件；
2. 链接：把类的二进制数据合并到JRE中；
      1. 校验：检查载入Class文件数据的正确性；
      2. 准备：给类的静态变量分配存储空间；
      3. 解析：将符号引用转成直接引用；
3. 初始化：对类的静态变量，静态代码块执行初始化操作

### 解读（Useless.class为例）：
```java
public class Useless {

    public Serializable s1 = new Serializable() {
        {
            System.out.println("域变量");
        }
    };

    public static Serializable s2 = new Serializable() {
        {
            System.out.println("静态域变量");
        }
    };
    public static int num = 3;

    static {
        System.out.println("静态代码块");
    }

    {
        System.out.println("代码块");
    }

}
```
- 装载即通过查找 Useless.class， 得到二进制码。并生产出该类的数据结构，得到一个Class对象。

- 校验即校验二进制码的数据，比如编译级别、是否符合Java规范等等

- 准备即为 s2 和 num 赋值 null 和 0。

- 解析, Java虚拟机会把类的二级制数据中的符号引用替换为直接引用。

- 初始化即另s2得到值，令num得到3。

> 可以看到， 类加载的整个过程跟域变量和代码块都是没什么关系的

## 类加载的一般方式

>方式一：
Class.forName
方式二
ClassLoader.loadClass


## 这里有必要说些SPI
 Java 的 SPI 机制，英文全称是 `Service Provider Interface`，常用于框架的可扩展实现。
比如使用 JDBC 连接数据库
当我们想使用 `MySQL` 数据库的时候，我们需要引入 `mysql `的驱动包。
而当我们使用 `SQLServer` 数据库的时候，我们需要引入 `SQLServer` 的驱动包。
但是我们在获取数据库连接的时候，却都是用同样的代码：
```java
Connection conn = DriverManager.getConnection(DB_URL,USER,PASS);
Statement stmt = conn.createStatement();
String sql = "SELECT id, name, url, comment FROM blog";
ResultSet rs = stmt.executeQuery(sql);
```
而且一般也不要改什么代码，直接换个数据库连接就行了，这其实就是SPI机制。

SPI机制详细解析参考：<https://www.cnblogs.com/chanshuyi/p/deep_insight_java_spi.html>

SPI机制实例：<https://github.com/chenyurong/song-parser-spi-demo>


`Java SPI `无处不在，通过使用 SPI 能够让框架的实现更加优雅，实现可插拔的插件开发。

