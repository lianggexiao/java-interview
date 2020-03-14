### MySQL索引、锁

------

```
mysql> show engines;
```

innodb: mysql 5.7 中的默认存储引擎。Supports transactions, row-level locking, and foreign keys

innodb 将用户数据存储在聚集索引中, 以减少基于主键的常见查询的 I/O。

##### InnoDB存储数据结构 - B+Tree

索引的常见模型常见的有，**哈希表、有序数组、搜索树**。
**有序数组**：优点是等值查询、范围查询都非常快；缺点也很明显，就是插入效率太低，因为如果从中间插入，要移动后面所有的元素。 

 **哈希结构**：适用于等值查询，不适用于范围检索例如'<'、'>'、"between and"等。 优化器不能使用哈希索引来加快 order by 操作。 哈希表，优点就是查询快，缺点是范围查询效率很低（因为无序）。适用于等值查询。 

**B树索引**可以适用于=、>、>=、<、<=、BETWEEN 等操作符。B树索引也可以用于LIKE比较，只有当LIKE的参数是一个字符串常量并且`不以通配符`开始才可以适用索引。 树结构，优点有序，并且多叉树可以减少磁盘I/O次数。 

B+Tree 与B树的区别：

B-Tree的结构和B+Tree结构类似，只是非叶子节点也会存储数据，而B+Tree只在叶子节点存储数据，虽然B-Tree可能在遍历到第二层时就可以得到数据返回，但是由于非叶子节点也会存储数据，导致每个数据页存储的索引更少，导致树的高度会很高，如果需要遍历的数据在叶子节点，则非常费时，所以查询性能不如B+Tree稳定。



##### B+Tree

主键索引的叶子节点存的是整行数据，非主键索引的叶子节点存的主键的值。 

在`InnoDB`里，主键索引被称为聚簇索引或聚集索引, 非主键索引被称为二级索引或辅助索引。 

在`InnoDB`中，表都是根据主键顺序以索引的形式存放的，这种存储方式的表成为索引组织表。每一个索引在`InnoDB`里对应一棵B+树，数据是有序排列的。

######  **聚簇索引生成规则**

定义主键用主键作为聚簇索引。  没定义主键使用第一个唯一非空索引作为聚簇索引。  没定义主键，也没定义唯一索引，生成一个隐藏的列作为聚簇索引。 

###### *基于主键索引和普通索引查询有什么区别？*

1. 如果sql是 select * from r where id = 1; 即通过主键方式查询，只需要搜索主键这棵B+树。
2. 如果sql是 select * from r where k = 10; 即通过普通索引查询，需要先搜索普通索引k这棵B+树，拿到主键id=1,在用id=1再去搜索主键索引的B+树。这个过程叫做*回表*。



在分析一个sql语句：select * from r where k between 8 and 22;

1. 在k索引树上找到k=10的记录，取得id=1;
2. 在id索引树上找到id=1的对应的行记录data(回表);
3. 在k索引树上找到k=20的记录，取得id=2;
4. 在id索引树上找到id=2的对应的行记录data(回表);
5. 在k索引树取下一个值k=30,不满足，循环结束。

*这个例子由于要查询的结果只有主键索引上面才有，所以不得不回表。那么如何避免回表？*



###### SQL优化

**覆盖索引**

如果sql语句是：select id from r where k between 8 and 22，由于这时只需要查询id值，而id值已经在k索引树上了，所以不需要回表查询，索引k已经覆盖了我们的查询需求，称之为覆盖索引。
 *由于覆盖索引可以减少数的搜索次数，显著提高查询性能，所以使用覆盖索引是一个常用的优化手段。*
 场景：假设有一个市民表：

```
CREATE TABLE `citizen` (
  `id` int(11) NOT NULL,
  `id_card` varchar(32) DEFAULT NULL,
  `name` varchar(32) DEFAULT NULL,
  `age` int(11) DEFAULT NULL,
  `ismale` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `id_card` (`id_card`),
  KEY `name_age` (`name`,`age`)
) ENGINE=InnoDB
```

 是否有必要创建身份证号和姓名的联合索引？
根据业务来看，如果有根据身份证号查询姓名的高频需求，可以考虑创建身份证号和姓名的联合索引，避免回表提高查询的效率。 

**最左前缀原则**

```
1. SELECT * FROM tbl_name WHERE key_col LIKE 'Patrick%';
2. SELECT * FROM tbl_name WHERE key_col LIKE 'Pat%_ck%';
```

 上面两句可以使用到索引 

```
3. SELECT * FROM tbl_name WHERE key_col LIKE '%Patrick%';
4. SELECT * FROM tbl_name WHERE key_col LIKE other_col;
```

 第三句由于以通配符开始，不符合最左前缀原则，所以不能适用索引。第四句，由于LIKE的参数不是一个字符串常量，所以也不使用索引。 

 如果用 LIKE '%string%' 字符串长度超过3，会使用串匹配的BM算法提高查询效率。
另外，如果某一列有索引，如果值为空，使用*where col_name IS NULL*也是可以走索引的。 

```
select * from citizen where name like '张%';
```

 这时也可以用上name的索引，查找到第一个以张开头的人，向后遍历直到不满足条件为止。
而如果要检索姓张，年龄10岁的男孩。 

```
select * from tuser where name like '张%' and age=10 and ismale=1;
```

 这个在MySQL5.6以前是要根据查询到姓张的人开始一个一个回表去查询age是否满足10的，而5.6引入了*索引下推优化*(index condition pushdown)，可以在遍历中，对索引中包含的字段先判断，过滤掉不满足的记录，减少回表次数。  



##### 如果业务满足某字段唯一，是否可以考虑用该字段作为主键？

例如居民身份证号可以保证唯一，那么是否用身份证号当做主键建表？

这里并太建议，根据上面介绍的聚簇索引和二级索引的结构之后，可以看出主键索引越长对于辅助索引建立需要更多的空间，另外对于聚簇索引，如果索引过长会导致主键索引树的高度变高，因为一个数据页默认是16k，主键索引越长则一个数据页能容纳的索引则越少。身份证号是18位，用字符串来存需要18个字节，而如果使用自增的long来做主键，则只有8个字节。

另一个好处就是自增主键可以保证插入只需要插入到数据页的队尾，不需要插入中间，而身份证号按照顺序排序有可能会插入中间位置，这样会导致数据页存满，数据页分裂等消耗。



##### 字符串应该如何创建索引？

 场景一，根据邮箱登录是一个普遍场景，如果邮箱不加索引则需要全表扫描，而如果加入全量索引则需要占用很大的空间。由于字符串索引支持最左前缀原则，则我们可以这样创建索引： 

```
alter table user add index index(email(5));
```

这里设置email的最左前5个字符作为索引可以缩小范围，但是如果前5个字符可能重复的数据很多，比如zhangsan@XX.com、zhangsi@XX.com、zhangwu@XX.com、zhangliu@XX.com、zhangqi@XX.com都会搜索出来在遍历，区别度太小，在某字段简历索引的一个原则就是这个字段的区别度，如此建立索引区别度太小。所以应该取得区别度可接受的最左前缀。

```
select count(distinct email) as L from user;（查询总数）
```

然后执行下列语句，来看每个前缀长度索引的区别度，找一个能够接受的长度，比如你的要求是区别度大于95%，那么可以算一下多长的前缀符合你的要求，区别度=L(n)/L。

```
select
count(distinct left(email,4) as L4,
count(distinct left(email,5) as L5,
count(distinct left(email,6) as L6,
count(distinct left(email,7) as L7,
from user;
```



场景二，还是身份证的场景，根据身份证进行等值查询，应该如何建立索引？ 提供两种方案：

1. 因为身份证前面都是省市生日等重复较多的信息，所以这里可以考虑倒序存储，并选择一个长度，比如倒数8位作为前缀索引。

```
select field_list from t where id_card = reverse('input_id_card_string');
```

2. 第二种是用hash，在创建一个身份证hash字段，用这个字段作为索引。 

```
alter table t add id_card_crc int unsigned, add index(id_card_crc);
```

 查询时候用以下语句： 

```
select field_list from t where id_card_crc=crc32('input_id_card_string') and id_card='input_id_card_string');
```

 这样可以先快速缩小结果集的范围，在根据结果集遍历来查询精确的身份证号，提高效率。
缺点：以上几种方式都不支持范围查询，可以自己根据业务场景自己选择合适的方式。 



##### 多版本并发控制

 mysql的MVCC(多版本并发控制) 是如何做到的？

 MVCC的实现是通过保存数据在某个时间点的快照来实现的。不同存储引擎实现的方式也不同。 

InnoDB的MVCC是通过在每行记录后面保存的两个隐藏列来实现的。两个列一个保存了行的创建时间，另个一保存了行的过期时间。这里其实保存的并不是具体时间，而是系统版本号(system version number)。每新开启一个事物，系统版本号都会自动递增，事物开始时刻的系统版本号会作为事物的版本号，用来和查询到的每行记录版本作为比较。



明天再看看

 https://juejin.im/post/5db19103e51d452a300b14c9 

 https://juejin.im/post/5e68dd5651882549564b6c28 