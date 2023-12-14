# COMP390 - Honours Year Computer Science Project - 2023/24

## Project Title:
Bring Powersort to Java

## Powersort:
https://www.wild-inter.net/publications/munro-wild-2018

## Code Reference

Java: https://github.com/sebawild/nearly-optimal-mergesort-code

C++: https://github.com/sebawild/powersort

## Currently changed files:
- [PowerSort.java](./src/java.base/share/classes/java/util/PowerSort.java)
- [ComparablePowerSort.java](./src/java.base/share/classes/java/util/ComparablePowerSort.java)
- [Arrays.java](./src/java.base/share/classes/java/util/Arrays.java)
- [ArraysParallelSortHelpers.java](./src/java.base/share/classes/java/util/ArraysParallelSortHelpers.java)

## Build JDK

https://openjdk.org/groups/build/doc/building.html


Optional - clean make if necessary
```bash
make dist-clean
```


Normal build

("`../jtreg/`" is my jtreg directory, change to yours if necessary)

```bash
bash configure --with-jtreg=../jtreg/
```

```bash
make images
```

## Test util library

```bash
make test TEST="jdk_util"
```

---

Following is the original README.md file from the OpenJDK repository.

# Welcome to the JDK!

For build instructions please see the
[online documentation](https://openjdk.org/groups/build/doc/building.html),
or either of these files:

- [doc/building.html](doc/building.html) (html version)
- [doc/building.md](doc/building.md) (markdown version)

See <https://openjdk.org/> for more information about the OpenJDK
Community and the JDK and see <https://bugs.openjdk.org> for JDK issue
tracking.
