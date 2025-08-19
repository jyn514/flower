---
aliases: ['/2018/03/03/Object-Oriented-Python.html']
title: "Object Oriented Python"
date: 2018-03-03
description: creating classes and objects in Python
taxonomies:
  tags:
    - python
---

# Intro
Python is a strange language.
You can do a lot with it, up to and including shooting your own foot off.

## Python allows

- array splicing

```python
>>> l = [1, 2, 3, 4, 5]
>>> l[:2]
[1, 2]
>>> l[2:]
[3, 4, 5]
>>> l[::2]
[1, 3, 5]
```

- appending lists with the '+' operator

```python
>>> [1, 2, 3] + [4, 5, 6]
[1, 2, 3, 4, 5, 6]
```

- using the first string of a progam as documentation

```bash
 % cat test.py
'''This is a docstring.'''
print(__doc__)
 % python $_
This is a docstring.
```

- renaming objects as they are imported

```python
>>> from sys import stdin as s
>>> s
<_io.TextIOWrapper name='<stdin>' mode='r' encoding='UTF-8'>
>>>
```

- modifying the type of a variable at runtime

```python
>>> import sys
>>> x = 2
>>> x = 'hi'
>>> x = sys.stdin
>>> print(x)
<_io.TextIOWrapper name='<stdin>' mode='r' encoding='UTF-8'>
```

- within a function, using a variable that has not yet been declared

```python
>>> def add_global():
...     global x
...     x += 2
...
>>> x = 0
>>> add_global(); print(x)
2
```

<br><br>

## Python disallows

- declaring types

```python
>>> int x = 2
  File "<stdin>", line 1
    int x = 2
        ^
SyntaxError: invalid syntax
```

- throwing errors before runtime

```python
>>> def add_local():
...     x += 2
...
>>> add_local()
Traceback (most recent call last):
  File "<stdin>", line 1, in <module>
  File "<stdin>", line 2, in add_local
UnboundLocalError: local variable 'x' referenced before assignment
```

- declaring and assigning a global variable in the same statement

```python
>>> def myfunc():
...     global x = 1
  File "<stdin>", line 2
    global x = 1
             ^
SyntaxError: invalid syntax
```

- bad indentation

```python
>>> for i in [1, 2, 3]:
... print(i)
  File "<stdin>", line 2
    print(i)
        ^
IndentationError: expected an indented block
```

- [tail call optimization](https://stackoverflow.com/questions/13591970/)
- [switch statements](https://www.python.org/dev/peps/pep-3103/)

In this article, I go over some of the things you can do with object-oriented python,
some things you should do, and some you really shouldn't.

# Objects in python
In other object-oriented languages I am familiar with, notably Java,
each object has its own methods you must learn to use.
[`size`, `length`, and `length()`](https://stackoverflow.com/questions/1965500/),
for example, are easily confused even for experienced developers.

In python, there is only one function, and it is not a property of the object.

```python
>>> len([1, 2, 3])
3
```

Similarly, all array-like objects can be treated as arrays:
```python
>>> "135"[2]
'5'
```

## How is this done?
In Java, C++, and every other language I can think of,
objects are essentially implemented as very complicated structs:
there are pointers to data and void pointers to functions.
All object pointers are void pointers at runtime;
this is safe because types are checked at compile time.
How would you implement a global type-independent `length` function
when you don't know what length means for that object?
The closest you could get is a wrapper:
```java
public static int length(T object) {
    switch(object.class) {
    case Array.class:
        return object.length;
    case List.class:
        return object.size();
    case String.class:
        return object.length();
    ...
}
```

It's a nightmare just to write 3 of those switch cases;
imagine how awful it would be for every object in `java.util`.

Well, it turns out, that wrapper is essentially what python does.
When you run `len(object)`, all it does is `return object.__len__()`!
It's the same for every other attribute -
accessing an item, iterating, the whole
[shebang](https://en.wikipedia.org/wiki/Shebang_(Unix)).

```python
>>> class obj:
...     def __len__(self):
...         return 4  # guarenteed by IEEE to be random
...     def __getitem__(self, index):
...         return 3
...     def __iter__(self):
...         yield 1
...
>>> o = obj()
>>> len(o)
4
>>> o[613]
3
>>> for i in o:
...     print(i)
...
1
```

We have to `yield` 1 instead of `return` because `iter` takes a
[generator](https://wiki.python.org/moin/Generators).

## Using objects
So, now we know how objects work -
all we have to do is implement the builtin methods.
How do you extend a class, though?
Java's entire paradigm is built around inheritance.

You extend classes in Python by passing them to the constructor:
```python
>>> class my_list(list):
...     def my_function(self):
...             print("Hello from my_list!")
...
>>> my_list()
[]
>>> my_list([1, 2])
[1, 2]
>>> my_list([1, 2])[0]
1
>>> my_list([1, 2]).my_function()
Hello from my_list!
```

And what's up with that pesky `self`, anyway?
```python
>>> class my_class:
...     def my_function():
...             print("Hello from my_function!")
...     def my_other_function(self):
...             print("Hello from my_other_function!")
...
>>> o = my_class()
>>> o.my_function()
Traceback (most recent call last):
  File "<stdin>", line 1, in <module>
TypeError: my_function() takes 0 positional arguments but 1 was given
>>> o.my_other_function()
Hello from my_other_function!
```

It turns out that Python automatically passes `self` to each method when it's called.
The reason for this is one of my least favorite parts of Python:
there's no class namespace. If you want to refer to an attribute of the instance,
you have to use `self`.
```python
>>> class obj:
...     def __init__(self):
...         self.num = 3
...     def f(self):
...         print(num)
...     def f2(self):
...         print(self.num)
...
>>> o = obj()
>>> o.num
3
>>> o.f()
Traceback (most recent call last):
  File "<stdin>", line 1, in <module>
  File "<stdin>", line 5, in f
NameError: name 'num' is not defined
>>> o.f2()
3
```

## Constructors and Destructors
So . . . about those.

When you instantiate an object in python, it calls
[`__new__` then `__init__`](https://stackoverflow.com/q/674304), in that order.
`__init__` is `None` by default; `__new__` may or may not be.
It's considered bad practice to define `__new__` in your program.

When an object is destroyed through garbage collection,
`__del__` is called.

This sounds simple enough so far.

If `__init__` is interrupted, `__del__`
[may or may not be called](https://stackoverflow.com/a/2433847).

If you use a `with` statement, i.e. `with open(filename) as f:`,
*neither `__init__` nor `__del__` are called*; `__enter__` and `__exit__`
are called instead.

I'm still confused by these.

## Conclusion
While python certainly supports objects,
I think it is harder to create objects in python than in other languages,
especially compared to the simplicity of the rest of the language.
You shouldn't have to know how the garbage collector handles references
to create a deconstructor (I haven't seen any syntax for deconstructors I liked,
but that's a different topic). You especially should *not* have to create
methods with two underscores before and afterwards to have a useful class.

`</rant>`

## Example

I'll leave you with an example from my research project:
```python
from keras.utils.data_utils import Sequence
from pandas import read_csv

class FileGenerator(Sequence):
    def __init__(self, data='AmPEP-master/combined_sequences.txt', batch_size=128,
                 ratio=3, labels='AmPEP-master/labels.txt'):
        self.positive_matches = 3286
        self.filename = open(filename, 'r')
        self.batch_size = batch_size
        self.length = (positive_matches + positive_matches * ratio) // batch_size

    def __len__(self):
        return self.length

    def __getitem__(self, index):
        data = read_csv(self.data, header=None, skiprows=(index * batch_size),
                        nrows=batch_size)[0]
        labels = read_csv(self.labels, header=None, skiprows=(index * batch_size),
                        nrows=batch_size)[0]
        return data, labels

    def __iter__(self):
        return (self[i] for i in range(len(self)))
```
