# COMP390 - Honours Year Computer Science Project - 2023/24

## Project Title:

Bring Powersort to Java

## Java Doc and Test Report

https://dubi253.github.io/openjdk/

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
- [TEST.groups](test/jdk/TEST.groups)
- [PowerSortTest folder](test/jdk/java/util/PowerSort/)
- [GitHub Actions CI/CD workflow files](.github/workflows/)

## Build Jtreg

clone jtreg repository

```bash
git clone https://github.com/openjdk/jtreg.git
```

add JAVA_HOME to your environment variables, **change to your java home** directory if necessary

```bash
export JAVA_HOME=/usr/lib/jvm/java
```

go to jtreg directory

```bash
cd jtreg
```

build jtreg

```bash
sh make/build.sh
```

add jtreg to your PATH

```bash
export JTREG_HOME=[your jtreg root directory]/build/images/jtreg
export PATH=$PATH:$JAVA_HOME/bin:$JTREG_HOME/bin
```

All done, you can now use jtreg to test your openjdk build.

```bash
jtreg -h
```

## Build OpenJDK

https://openjdk.org/groups/build/doc/building.html

Optional - clean make if necessary

```bash
make dist-clean
```

Normal build

If you have jtreg installed and env PATH, you can run the following command to build the JDK:

```bash
bash configure
```

("`../jtreg/`" is my jtreg directory, change to yours if necessary)

```bash
bash configure --with-jtreg=../jtreg/
```

```bash
make images
```

## Test Powersort

You can run the test with any of the following commands:

```bash
make test TEST="jdk_powersort"
```

```bash
 jtreg -verbose:all -jdk:build/linux-x86_64-server-release/jdk test/jdk/java/util/PowerSort/PowerSortTest.java
```

## Flame Graphs Visualisation

Install AsyncProfiler at https://github.com/async-profiler/async-profiler

Enable perf_event_paranoid and disable kptr_restrict for async-profiler to work

```bash
sudo sysctl kernel.perf_event_paranoid=1 && sudo sysctl kernel.kptr_restrict=0
```

List all Java processes

```bash
jps -l
```

Run AsyncProfiler and generate flame graph

```bash
./asprof -d 400s -f ./tmp.html [PID]
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
