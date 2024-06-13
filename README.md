# example-uplist-plugin
A example uplist plugin to Rukkit\
示例 Rukkit 列表公布插件

# Complie
Get a gradle environment and run
```shell
gradle shadowJar
```
zip okio and okhttp into the build/libs/example-uplist-plugin.jar,and drop the jar to your server plugins dir.

# Console command
```shell
publish start # Start to publish a server.

publish status # Check the publish info.

publish stop # Stop the publish.

publish motd <MOTD> # Change the MOTD of Server.
```

# Thanks

api.data.der.kim ([@deng-rui](https://github.com/deng-rui)) provide the publish API support.

# 编译
在 gradle 环境下执行
```shell
gradle shadowJar
```

# 有关命令
```shell
publish start # 启动列表公开

publish status # 查看公开状态

publish stop # 停止列表公开

publish motd <MOTD> # 更改服务器标题 (MOTD)
```

# 特别鸣谢
感谢 api.data.der.kim ([@deng-rui](https://github.com/deng-rui)) 提供有关列表公开的 API 支持。