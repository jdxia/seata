# Seata 客户端源码学习

本目录包含两个客户端模块。先运行 Spring Boot 模块，观察 AT 提交和回滚；再运行 Spring Cloud Alibaba 模块，观察 Feign 传播 XID 和跨库回滚。

| 模块 | 启动类 | 默认端口 | 数据库 |
| --- | --- | --- | --- |
| `seata-learning-spring-boot-client` | `BootClientApplication` | `18081` | `seata_learning_boot` |
| `seata-learning-spring-cloud-alibaba-client` | `CloudClientApplication` | `18082` | `seata_learning_cloud` |

## 版本与依赖

- JDK 17、Spring Boot `3.2.12`、Spring Cloud `2023.0.1`、Spring Cloud Alibaba `2023.0.1.0`。
- Seata 固定为当前源码版本 `2.1.0`，业务代码使用 `org.apache.seata` API。
- SCA `2023.0.1.0` 的官方基线是 Boot `3.2.4`、Seata `2.0.0`。本例升级 Boot 补丁版本，排除 SCA 引入的 `io.seata:seata-spring-boot-starter`，使用当前工程的 `org.apache.seata:seata-spring-boot-starter:2.1.0`。
- SCA 的拦截器仍调用 `io.seata` API。Seata `2.1.0` 的兼容层会转交给 `org.apache.seata` 实现，共享同一个 XID 上下文。不要再引入 Seata `2.0.0` 的 jar。
- 学习模块使用独立父 POM，不改变服务端和框架模块的 Spring Boot、Java 版本。
- 排除框架 RPC 集成间接带入的旧 `groovy-all`。这两个 REST 客户端不使用 Groovy 模板，避免 Boot 自动配置模板引擎时触发 Java 17 反射访问错误。
- 客户端通过 Nacos 查找 TC，Seata 事务配置保留在本地 `application.yml`。库存服务仍使用固定 HTTP 地址，SCA starter 负责 Feign 的事务上下文传播。

官方版本表：https://sca.aliyun.com/docs/2023/overview/version-explain/

## 1. 准备数据库和服务端

仅连接自己的学习数据库。执行本目录的建表脚本：

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p < learning/schema.sql
```

脚本创建两个独立数据库、库存表、订单表和各库的 `undo_log`。首次插入 100 个库存；再次执行不会清空订单或重置库存。应用启动不自动建表、不清空数据。

启动当前项目的 `org.apache.seata.server.ServerApplication`。当前服务端配置使用 Nacos 注册和配置管理；确认 `192.168.33.20:8848` 中 namespace ID 为 `seata`、group 为 `SEATA_GROUP`、服务名为 `seata-server-demo`，且 `tradeOrderTxCluster` 下存在健康的 TC 实例。默认 HTTP 端口为 `7091`，TC 业务端口为 `8091`。客户端连接 Nacos 返回的 TC 业务地址，该地址必须从客户端可达。

客户端共同使用 `trade_order_tx_group -> tradeOrderTxCluster -> Nacos 返回的 TC 地址`。客户端的 `config.type=file` 与服务端的 `config.type=nacos` 可以不同；映射由客户端本地配置提供，不需要读取服务端的配置 Data ID。TC 实际使用 DB 存储时，需要先执行 `script/server/db/mysql.sql` 创建 TC 存储表；两个业务数据库仍需各有一张 `undo_log`。服务端启用 Nacos 配置中心后，应核对远端 `seataServerDemo.properties` 中实际生效的存储配置。

`spring.datasource` 是客户端业务数据源，与 TC 的 `seata.store.db` 分开配置。当前两个客户端的 JDBC URL 均指向 `seata`；若使用本目录脚本创建的两个学习库，请分别用 `SPRING_DATASOURCE_URL` 覆盖为对应学习库的完整 JDBC URL，并保留所需连接参数。

## 2. 在 IDEA 中运行当前源码

在 IDEA 中重新加载根项目的 Maven 配置。本目录及两个客户端已加入默认模块列表，无需启用额外 profile。使用 JDK 17。

在根目录构建客户端及其依赖，跳过框架原有测试：

```bash
mvn -pl learning/spring-boot-client,learning/spring-cloud-alibaba-client -am \
  -Dmaven.test.skip=true package
```

Apple Silicon 上，框架原有的 Protobuf `3.11.0` 没有 macOS ARM 编译器包。请使用项目已有的 `arrch64` profile，在上面的命令中增加 `-Parrch64`。该 profile 使用 x86_64 编译器，需要系统已安装 Rosetta。下面的 `install` 命令也相同。

在两个启动类的 IDEA 运行配置中设置自己的数据库和 Nacos 环境变量：

| 变量 | 默认值 / 用途 |
| --- | --- |
| `DEMO_DB_USERNAME` | `root` |
| `DEMO_DB_PASSWORD` | 当前示例默认密码；请设置自己的密码 |
| `SPRING_DATASOURCE_URL` | 覆盖业务 JDBC URL；Boot 指向 `seata_learning_boot`，Cloud 指向 `seata_learning_cloud` |
| `NACOS_USERNAME` | `nacos`，用于发现 TC |
| `NACOS_PASSWORD` | `nacos`，用于发现 TC |
| `STOCK_SERVICE_URL` | `http://127.0.0.1:18081` |

先 Debug `BootClientApplication`，再 Debug `CloudClientApplication`。使用各自的模块 classpath。通过根 reactor 加载时，IDEA 可以关联当前工程中的 Seata 模块。断点应落在当前工程的 `spring/`、`tm/`、`rm-datasource/` 等源码中。

如果只单独导入 `learning/pom.xml`，Maven 可能使用本地仓库或远程仓库中的 Seata 发布包；这不能证明运行的是当前工作区源码。需要调试修改后的 Seata 时，请导入根项目并重新构建。

也可以运行刚构建的可执行 jar：

```bash
java -jar learning/spring-boot-client/target/seata-learning-spring-boot-client-2.1.0.jar
java -jar learning/spring-cloud-alibaba-client/target/seata-learning-spring-cloud-alibaba-client-2.1.0.jar
```

以上两个命令分别在不同终端运行，并事先设置数据库环境变量。

## 3. 单个 Spring Boot 客户端

```bash
# 查看库存
curl http://127.0.0.1:18081/demo/stock

# 提交：库存减 1，响应包含 xid
curl -X POST http://127.0.0.1:18081/demo/transactions

# 回滚：先扣库存，再主动抛异常，预期 HTTP 409，事务完成后库存不变
curl -i -X POST 'http://127.0.0.1:18081/demo/transactions?rollback=true'
```

`StockController -> StockService.execute -> @GlobalTransactional -> StockService.deduct -> JdbcTemplate`。

`POST /demo/stock/deduct` 是供另一个客户端使用的分支入口。没有传播 XID 的直接请求会返回 HTTP 400，避免把本地提交误当作全局事务。

## 4. Spring Cloud Alibaba 跨服务事务

```bash
# 查看已提交订单数量和库存
curl http://127.0.0.1:18082/demo/orders
curl http://127.0.0.1:18081/demo/stock

# 提交：订单数加 1，库存减 1；响应中 xid 与 stockXid 必须相同
curl -X POST http://127.0.0.1:18082/demo/orders

# 回滚：订单插入和库存扣减都完成后抛异常，预期 HTTP 409，两库均恢复
curl -i -X POST 'http://127.0.0.1:18082/demo/orders?rollback=true'
```

调用链：

```text
CloudClient :18082（TM + 订单 RM）
  OrderService.create：开启全局事务、插入订单
    -> SCA SeataFeignRequestInterceptor：将 XID 放入 TX_XID 请求头
    -> BootClient :18081（库存 RM）
       JakartaTransactionPropagationInterceptor：绑定 XID
       StockService.deduct：扣减库存
  正常返回 -> TC 通知分支提交
  rollback=true -> TC 通知两个分支回滚
```

两库业务数据的最终结果才是验证依据。HTTP 请求、连接成功或分支注册成功，不能单独证明全局事务成功。

## 5. 建议的断点

按这个顺序跟踪：

1. `SeataAutoConfiguration.globalTransactionScanner`：注册扫描器、初始化 TM/RM。
2. `GlobalTransactionalInterceptorHandler`、`TransactionalTemplate.execute`：进入全局事务。
3. `DefaultGlobalTransaction.begin/commit/rollback`：向 TC 发起事务请求。
4. `ConnectionProxy.commit`、`AbstractDMLBaseExecutor.executeAutoCommitTrue`：本地 SQL、分支注册、undo_log。
5. SCA 的 `SeataFeignRequestInterceptor.apply`，以及 Seata 的 `JakartaTransactionPropagationInterceptor.preHandle`：跨进程传递并绑定 XID。
6. `DataSourceManager.branchRollback`、`AbstractUndoLogManager.undo`：回滚业务数据。

全局事务和 Feign 读取超时均设为 300 秒，便于打断点。暂停超过此时间可能触发超时回滚。TC 恢复线程继续运行时，也可能先处理超时事务。

AT 一阶段会提交本地事务，同时保存 undo_log；回滚时根据 undo_log 恢复。二阶段提交后 undo_log 会异步清理。想观察中间状态，可以在业务写入后、方法返回前暂停，而不是只在整个请求结束后查询。

## 6. 验证

此前使用固定 TC 地址，在 Java 17、MySQL 8.4 和当前源码构建的 Seata 2.1.0（file 存储）上验证：4 个集成测试通过，两个可执行 jar 的提交、回滚和跨服务 XID 检查通过。该结果不代表当前 Nacos 配置已完成集成验证。重复执行建表脚本保留已有库存和订单。

集成测试需要真实 MySQL、已建好的学习表和运行中的 TC，默认跳过，避免普通构建连接外部数据库。

先构建并安装工作区客户端依赖：

```bash
mvn -pl learning/spring-boot-client,learning/spring-cloud-alibaba-client -am \
  -Dmaven.test.skip=true install
```

启动库存服务 `BootClientApplication`，然后运行：

```bash
mvn -f learning/pom.xml -Dlearning.integration=true test
```

测试使用上表的数据库环境变量。它会检查单库提交和回滚、拒绝缺少 XID 的调用、跨服务 XID 一致，以及订单和库存的跨库回滚。测试中的成功请求会消耗两个库存，并保留一个订单；不要对非学习数据库运行。

## 边界

这些接口只监听 `127.0.0.1`，用于本地学习，没有业务鉴权。库存为演示用整数数量，不涉及资金。库存耗尽后成功用例会失败；如需重置，请在确认学习库名称后手动修改 `learning_stock`。应用和测试都不会自动清库。
