---
aliases: ['/2018/03/29/Copying-Generic-Arrays-in-Java.html']
title: "Copying Generic Arrays in Java"
date: 2018-03-29
description: The problem that's been bothering me for a week
taxonomies:
  tags:
    - java
---

# Intro

Copying an array.
It sounds so simple - run a quick `for` loop,
or better yet, `System.arraycopy`.
```java
System.arraycopy(array, 0, tmp, 0, array.length);
```

What if you need to perform a destructive operation? <sup><a href="#1">[1]</a></sup>
In my Data Structures class, we're asked to implement heap sort
without modifying the original array.
This is simple enough: use `array.clone`.
It preserves the order of the original array while giving you a new instance.
```java
T[] tmp = array.clone();
```

Remember that this is a destructive operation, however;
you need a new third array in which to store the result. 
Only one problem: Java doesn't allow generic arrays to be created.
Trying to do so is a compile-time error:
```java
class Generic<T> {
    Generic () {
        System.out.println(new T[0]);
    }

    public static void main(String[] args) {
        new Generic<String>();
    }
}
```
```sh
 % javac Generic.java
Generic.java:3: error: generic array creation
        System.out.println(new T[0]);
                           ^
1 error
```

Fine, let's do some [ugly casting](https://stackoverflow.com/a/530289).

`T[] array = (T[]) new Object[0];`

# Strange happenings
So far, this has all been pretty standard.
Java has [type erasure](https://docs.oracle.com/javase/tutorial/java/generics/erasure.html), Java generics are a pain, yadda yadda.
Now we get to the strange part of this post.

```java
class Copy<T> {
    private final T[] array;
    Copy(T[] array) {
        this.array = array;
    }

    T[] getNew() {
        T[] result = (T[]) new Object[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = array[i];
        }
        return result;
    }

    public static void main(String[] args) {
        Integer[] arr = new Copy<Integer>(new Integer[] {1, 2, 3}).getNew();
    }
}
```
```sh
 % javac copy.java
Note: copy.java uses unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.
 % java copy 
Exception in thread "main" java.lang.ClassCastException: [Ljava.lang.Object; cannot be cast to [Ljava.lang.Integer;
        at copy.main(copy.java:16)
```

What?!‽
Not only are we getting a `ClassCastException` trying to convert `Integer` to `Integer`,
but we're getting it at runtime, not compile-time!
Worse yet, there's no traceback - the error pointed to `main`, not to `getNew`!

# My solution
What happens when you declare a new array in Java?
You might assume that this is translated directly to Java bytecode, but you would be incorrect.
`javac` actually translates this to `java.lang.reflect.Array.newInstance`, which in turn
[calls](http://hg.openjdk.java.net/jdk/jdk/file/1f9dd2360b17/src/java.base/share/classes/java/lang/reflect/Array.java#l76)
the native method `java.lang.reflect.Array.newArray`.
In fact, you can call this method yourself, but only if you have the class type.
Type parameters don't cut it.

Well, if you have an instance of the class, you can call `instance.getClass()`.
If you don't, you can (in this particular case) return a null pointer,
since the array must be empty.
```java
 % cat copy.java
class Copy<T> {

    private final T[] array;

    Copy(T[] array) {
        this.array = array;
    }

    T[] getNew() {
        if (array.length == 0) return null;
        T[] result = (T[]) java.lang.reflect.Array.newInstance(array[0].getClass(), array.length);

        for (int i = 0; i < array.length; i++) {
            result[i] = array[i];
        }

        return result;
    }

    public static void main(String[] args) {
        System.out.println(new Copy<Integer>(new Integer[] {1, 2, 3}).getNew());
    }
}
```
```sh
% javac $_
Note: copy.java uses unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.
jyn@debian-thinkpad ~/Documents/Programming/Java/test
 % java copy 
[Ljava.lang.Integer;@28d93b30
```

# Conclusion
I still have no idea what's going on here - my best guess is that due to type erasure,
the [JIT compiler](https://stackoverflow.com/a/95679) doesn't know that the objects in
`array` are actually valid `Integer`s.


<div class="footnote" name="1"><sup>1</sup>For the purpose of this post, I assume that only a shallow copy is necessary.</div>
