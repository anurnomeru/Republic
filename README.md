# Republic
Republic 是一款简单易用的 RPC 框架，基于 raft 实现了高可用的注册中心，基于 kotlin 开发，支持 Java、Kotlin。它并不需要寄托于 spring 环境使用，依赖极少，只使用了 netty、hessian、以及 guava。初版的 QPS 不算太高，本地测试在 6000 左右。

除了 RPC 相关功能，Republic 也自实现了 Bean 管理、注入、Aop、Listener 等功能。

### 一、Quick Start

##### 1. 启动注册中心
启动 `server` 模块下的 `Bootstrap` 来启动注册中心

```java
# enable the debug mode to prevent follower become candidate when did not received heart beat from leader for while
inet.localAddr=127.0.0.1:60001
#
# addr config composed with client.addr.{serverName}={host}:{port}
#
inet.clusterAddr=127.0.0.1:60001;127.0.0.1:60002
#
# elect control
#
elect.electionTimeoutMs=1500
elect.votesBackOffMs=700
elect.heartBeatMs=750
```

注册中心支持集群模式，推荐启动 3 个以上的 server，其余两个配置如下：

```java
# enable the debug mode to prevent follower become candidate when did not received heart beat from leader for while
inet.localAddr=127.0.0.1:60002
#
# addr config composed with client.addr.{serverName}={host}:{port}
#
inet.clusterAddr=127.0.0.1:60001;127.0.0.1:60003
#
# elect control
#
elect.electionTimeoutMs=1500
elect.votesBackOffMs=700
elect.heartBeatMs=750
```


```java
# enable the debug mode to prevent follower become candidate when did not received heart beat from leader for while
inet.localAddr=127.0.0.1:60003
#
# addr config composed with client.addr.{serverName}={host}:{port}
#
inet.clusterAddr=127.0.0.1:60002;127.0.0.1:60003
#
# elect control
#
elect.electionTimeoutMs=1500
elect.votesBackOffMs=700
elect.heartBeatMs=750
```

##### 2.服务提供者

使用注解 @RepublicBean 来标明一个 Bean 是供给 RPC 调用的，可参考 `providerDemo` 模块下的示例，服务提供者需要实现某个接口（并不是强制的，但建议这么做）。

```java
@RepublicBean
class SimpleProviderImpl : SimpleProvider {
    private val counter = AtomicInteger()

    override fun foo(): String {
        return "" + counter.addAndGet(1)
    }
}
```

启动该模块下的 `App`

##### 3.服务消费者

使用注解 @Republic 来注入一个经过动态代理的接口，这个接口的定义和服务提供者必须一致（接口名、方法等），但不一定要是同一个接口。可参考 `consumer` 模块下的示例。

```Java
@NigateBean
open class SimpleConsumer {

    @Republic
    private lateinit var simpleProvider: SimpleProvider

    fun rpcLoop() {
        simpleProvider.foo()
    }
}
```

启动该模块下的 `App` 即可。

### 二、Roadmap
 - 提高 QPS
