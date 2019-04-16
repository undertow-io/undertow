# Undertow Benchmarks

## JMH

Benchmarks use the [JMH harness](https://openjdk.java.net/projects/code-tools/jmh/).

## Running

```bash
mvn install
java -jar benchmarks/target/undertow-benchmarks.jar
```

Alternatively benchmarks may be run from the IDE using a JMH plugin like
[this one for idea](https://plugins.jetbrains.com/plugin/7529-jmh-plugin).