Moki [![Build Status](https://travis-ci.org/Unisay/moki.svg?branch=master)](https://travis-ci.org/Unisay/moki)
====
###Purely functional black-box testing toolkit for Scala

Wikipedia defines [black-box testing](https://en.wikipedia.org/wiki/Black-box_testing) as:

> Black-box testing is a method of software testing that examines the functionality of an application without peering into its internal structures or workings. This method of test can be applied virtually to every level of software testing: unit, integration, system and acceptance. 

The application (system) under test (further: [SUT](https://en.wikipedia.org/wiki/System_under_test)) rarely lives in isolation. Very often there are multiple external components that SUT depends upon: databases, file-systems, micro-services or any external OS processes (local or remote). Such dependencies together comprise an application environment that has to be up and running during tests. 

Relying on real production instances of SUT dependencies managed by 3-rd parties comes with a disadvantages:

* External dependency might be still under development and not yet available (only its API contract).
* Its not guaranteed that dependency is running each time a black box test is executed because it is controlled by other dev team;
* Its hard to simulate failure modes using production systems
    1. How would SUT behave if its dependency is being overloaded or shut down?
    1. Simulating slow responses in order to test timeouts. 
* Its hard to test functionality that modifies external data without such modifications being observable by end-users.

In order to mitigate such drawbacks developer could use mocks that simulate relevant functions of real external dependencies. For example, its a common practice to use embedded databases (like HSQLDB and H2) instead of full-fledged DB servers, or mock servers like www.mock-server.com to simulate external REST services. One could also locally run a docker image of the dependency.
 
 Managing such dependencies can quickly become tedious and error-prone: Start before the test, make sure there are no TCP port conflicting, shut them down after test or in case of abrupt termination.
 
 Moki toolkit does exactly this: it defines a `TestService` monad and provides a way to compose many of them into an environment that is started before SUT and safely shut down after. It handles failures gracefully. Please consider the following scenario involving 3 test services - Database ("DB"), Micro-service providing REST API ("API") and Email server ("Email"):

```
Start DB (db port 5432)
  Start EMAIL (email socket /var/sock/email)
    Start API (exposed on URL http://localhost:12345)
      Start SUT (depends on db port, email socket and API url, exposes http://localhost:8080)
        Run test that uses SUT URL http://localhost:8080
      Stop SUT
    Stop API
  Stop EMAIL
Stop DB
```

Any step can fail; its important that no test service is left running after test:

SUT failed on start:
```
Start DB
  Start EMAIL
    Start API
      Start SUT  ---> Exception thrown during start
        Run test <--- Skipped
      Stop SUT <-- Skipped
    Stop API 
  Stop EMAIL
Stop DB
```

Test service failed on start:
```
Start DB
  Start EMAIL
    Start API --> Exception thrown
  Stop EMAIL
Stop DB
```

Test service failed during shutdown:
```
Start DB
  Start EMAIL
    Start API
      Start SUT
        Run test
      Stop SUT
    Stop API <--- throws Exception 
  Stop EMAIL 
Stop DB
```

In all this cases Moki guarantees that every test service is stopped and not abandoned.

## Installation

```
resolvers += "unisay-maven" at "https://dl.bintray.com/unisay/maven"
libraryDependencies += "com.github.unisay" %% "moki" % "5.1.0" % "test,it"
```
## Usage

Main concept is a `TestService[R]` that can be started and stopped; Type parameter `R` is a type of resource that is available to SUT while service is started. Examples are: URL, socket, TCP port, folder on FS.

Its created like this:
```scala
val testService: TestService[Resource] = 
  TestService(start: Task[Resource], stop: Resource => Task[Unit])
```

Moki uses `fs2.Task` from [functional streams for scala](https://github.com/functional-streams-for-scala/fs2) to capture side-effectful computations.


Given 3 or more test services they can be composed like monads:
```scala
val serviceA: TestService[A] = ???
val serviceB: A => TestService[B] = ???
val serviceC: (A, B) => TestService[Unit] = ???

val environment = for {
  a <- serviceA
  b <- serviceB(a)
  _ <- serviceC(a, b)
} yield (a, b)

// or with applicative syntax:

val environment = (serviceA |@| serviceB |@| serviceC)((a: A, b: B, _) => (a, b))
```
The environment, in turn, can run "around" the task that contains a test functionality like this:

```scala
val test: Task[Assertion] = environment run { case (a, b) => 
  // verify something using a and b
  httpGetFrom(a) mustEqual readFileFrom(b)
}

test.unsafeRun()
```

Moki runs it in the following order:

```scala
    a = serviceA.start
      b = serviceB.start
        serviceC.start
          assertion = httpGetFrom(a) mustEqual readFileFrom(b)
        serviceC.stop
      serviceB.stop
    serviceA.stop
```
returning assertion to your test framework (ScalaTest or Specs2)

## License

The MIT License (MIT)

Copyright (c) 2017

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
