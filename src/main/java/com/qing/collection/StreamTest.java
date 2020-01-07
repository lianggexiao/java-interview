package com.qing.collection;

import com.qing.model.Person;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Java 8 发布至今也已经好几年过去，如今 Java 也已经向 11 迈去，
 * 但是 Java 8 作出的改变可以说是革命性的，影响足够深远，学习 Java 8 应该是 Java 开发者的必修课。
 * 这里会介绍流的相关操作
 */
public class StreamTest {

    public static void main(String[] args) {
        List<Person> list = new ArrayList<>();
        list.add(new Person("jack", 20));
        list.add(new Person("mike", 25));
        list.add(new Person("tom", 30));
        list.add(new Person("louis", 23));
        list.add(new Person("mike", 25));

        StreamTest test = new StreamTest();
        test.testFilter(list);
        test.testMap(list);
        test.testDistinct(list);
        test.testSorted(list);
        test.testMatch(list);
        test.testReduce(list);
        test.testJoining(list);
    }

    /**
     * filter(T -> boolean) 保留 boolean 为 true 的元素
     * 保留年龄为 20 的 person 元素
     * @param list
     */
    public void testFilter(List<Person> list) {
        List<Person> result = list.stream()
                .filter(person -> person.getAge() == 20)
                .collect(Collectors.toList());
        System.out.println("list = [" + result + "]");
    }

    /**
     * 将person的集合转换成字符串集合
     * 1.将集合转换为流，2.将List<Person>集合转换成List<String>，
     * 3.排序， 4.只保留前面三个， 5.将流转换回集合类型
     * @param list
     */
    public void testMap(List<Person> list) {
        List<String> result = list.stream()
                .map(Person::getName)
                .sorted()
                .limit(3)
                .collect(Collectors.toList());
        System.out.println("list = [" + result + "]");

        // 可以对返回结果操作
        List<String> result1 = list.stream().map(person -> {
            return person.getName() + "11";
        }).limit(3).collect(Collectors.toList());
        System.out.println("list = [" + result1 + "]");
    }

    /**
     * 去重
     * @param list
     */
    public void testDistinct(List<Person> list) {
        List<Person> result = list.stream()
                .distinct()
                .collect(Collectors.toList());
        System.out.println("list = [" + result + "]");
    }

    /**
     * 排序
     * @param list
     */
    public void testSorted(List<Person> list) {
        List<Person> result = list.stream()
                .sorted((p1, p2) -> p1.getAge() - p2.getAge())
                .collect(Collectors.toList());
        System.out.println("list = [" + result + "]");
    }

    /**
     * 流中是否有一个元素匹配给定的 T -> boolean 条件
     * 是否存在一个 person 对象的 age 等于 20：
     * @param list
     */
    public void testMatch(List<Person> list) {
        boolean result1 = list.stream().anyMatch(person -> person.getAge() == 20);
        boolean result2 = list.stream().allMatch(person -> person.getAge() == 20);
        boolean result3 = list.stream().noneMatch(person -> person.getAge() == 20);
        System.out.println("result["+ result1 + "," + result2 + "," + result3 + "]");
    }

    /**
     *  计算
     * @param list
     */
    public void testReduce(List<Person> list) {
        // 计算年龄总和：(0表示初始值为0)
        int sum1 = list.stream().map(Person::getAge).reduce(0, (a, b) -> a + b);
        // 与之相同:
        int sum2 = list.stream().map(Person::getAge).reduce(0, Integer::sum);
        // 与之相同:
        int sum3 = list.stream().mapToInt(Person::getAge).sum();

        System.out.println("result["+ sum1 + "," + sum2 + "," + sum3 + "]");
    }

    /**
     * 将结果连接成字符串
     * @param list
     */
    public void testJoining(List<Person> list) {
        String s = list.stream().map(Person::getName).collect(Collectors.joining(","));
        System.out.println("result["+ s + "]");
    }
}
