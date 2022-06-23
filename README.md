# Vine Project

High-performance proxy library based on Netty. Support socks/http mixed protocol, upstream proxy, MITM, intercept and tamper with https traffic.

## Features

- [x] HTTP
- [x] SOCKS4/4a
- [x] SOCKS5
- [x] Username/password authentication
- [x] Chained Upstream proxies
- [x] Http interceptor
- [ ] Rate monitor

## Usage

pom.xml
```xml
<dependency>
  <groupId>io.github.bynix</groupId>
  <artifactId>vine-core</artifactId>
  <version>1.0.0.alpha1-SNAPSHOT</version>
</dependency>
```

Demo.java
```java
public class Demo {

    public static void main(String[] args) {
        new VineServer.Builder()
            .withPort(2228)
            .build().start();
    }

}
```

## License

This project is licensed under the Apache License(Version 2.0) - see the [LICENSE](/LICENSE) file for details.
