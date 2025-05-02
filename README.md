# java-dst
This repository contains an early-stage library for implementing deterministic simulation testing in Java. The goal is to enable mostly idiomatic Java code to be tested deterministically, drawing inspiration from the FoundationDB and TigerBeetle projects.

Despite its many advantages, deterministic simulation is not widely adopted because most programming languages do not have determinism built in. As a result, libraries and codebases must actively avoid non-deterministic language features and dependencies. However, with the introduction of virtual threads in Java, it is now possible to control execution order at a much lower level, making deterministic testing significantly more feasible in Java.

| Java Non-determinism| Why | TLDR  | Mitigations |
| ---------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ | -------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| JVM Garbage Collection | Pause and cause thread reorder beyond what we can simulate.  | Ignore| It doesn't have a huge effect in the future its possible to implement a GC though.  |
| JIT  | Can change instruction ordering at runtime and cause very subtle bugs in multithreaded applications see JCStress project for more details. | Ignore/Disable | Disable it or turn it down during simulation if it is causing issues and leave that state space testing to tools like JCStress|
| Classloading  | Since it has to reads the file system.  | Ignore| Classloading it doesn't happen during runtime all that often so we can ignore it |
| volatile| since interlieved updates to the volitile can't be simulated | Avoid | Specifically avoid mutable volatile usage int, float, double, and long. Boolean and Immutable objects are okay.|
| Thread.sleep()| Since it sleeps based system time | TBD| Possibly replace usage with a proxy that uses simulation clock when run simulation. |
| Calendar, Date, System.nanoTime(), System.currentTimeMillis() | Returns values based on system time  | Alternative | Ignore things that are just timestamps or display. Use new java api with now(clock) instead. |
| LocalDate.now(), LocalTime.now(), Instant.now(), LocalDateTime.now(), ZoneDateTime.now() | Returns values based on system time  | Instrument  | Anything that can control logic will need to be replaced by something that can have the clock replaced e.g. Instant.now(Clock).  |
| Thread.ofPlatform(), ThreadPoolExecutor or Thread.ofVirtual() | Can only control interleaving of threads with virtual threads| Instrument  | Replace with SchedulableVirtualThreadFactory during simulation.|
| ScheduledExecutorsService | Uses system time to schedule tasks| Instrument  | Replace with SimulationScheduledExecutor  |
| Timer|  | Alternative | Use ScheduledExecutor instead |
| [java.io](http://java.io/).File\*  | Synchronous blocking calls can't be simulated | Alternative | use [java.](http://java.io/)nio.file instead |
| java.nio.file | IO operations all have to be simulated  | Instrument  | Replace FileSystem with JimFS during simulation |
| [java.net](http://java.net/).Socket| IO operations all have to be simulated  | Alternative | Use Netty instead with netty 4.2+ we can pass SchedulableVirtualThreadFactory into the event loop and use local transport to simulate network all within the JVM. |
| Native system calls |  | Don't Support  | |
| Random, SecureRandom| Randomness has to be made deterministic | Instrument  | Instances of Random and SecureRandom replaced with one instance of Random seeded from the simulation. |
| ForkJoinPool, .stream().parallel() | Can only control interleaving of threads with virtual threads| TBD| Need to look into scheduling virtual threads on custom fork join pool and replacing the default system fork join pool.  |
| Thread.join() | Blocking calls will cause the simulation to halt | Don't Support  | |
| External calls HTTP/2, GRPC, AMQP, MQTT, HTTP, STOMP ect...| Have to stub out all external calls to drive the simulation  | Instrument  | Netty stubs??? |
| External database calls| Have to stub out all external calls to drive the simulation  | TBD| Stub with h2?  |