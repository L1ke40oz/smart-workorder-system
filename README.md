# 知序工单 · 智能工单处理系统

Android 课程项目，依据 SPEC v1.1 独立开发。Android 使用 Java / XML / MVVM，后端使用 Spring Boot / JPA / REST API。

## 目录与文档

- `app/`：Android 客户端。
- `backend/`：Java 17 后端。
- [需求与实施计划](docs/需求与实施计划.md)
- [架构设计与技术决策](docs/架构设计.md)
- [持续开发记录](docs/开发记录.md)
- [API 接口](docs/API接口.md)
- [开发流程与报告素材](docs/开发流程与报告素材.md)
- [测试与验收](docs/测试与验收.md)

## 当前实现范围

已实现注册登录、两个角色首页、文字工单、确认提交、列表搜索与筛选、详情、接单、完成、处理结果查询、基础统计；还包括 Gemini 分析预览、硅基流动 embedding、历史工单 Lucene 向量检索和处理参考建议。

基础业务经过 MySQL 和 Android 模拟器验收。智能模块的 HTTP 请求、解析、检索与业务逻辑通过本地供应商测试接口验证；真实模型名、密钥及网络联通尚待配置。语音、图片和云部署属于后续工作。

## 本地启动

先在终端启动后端（需要 JDK 17 和 Maven）：

```powershell
cd D:\Savedfiles\AndroidStudioProjects\smartworkordersystem\backend
mvn spring-boot:run
```

默认 `dev` 模式使用 H2 文件数据库，数据保存在 `backend/data/`，无需先配置 MySQL。不开放 H2 网页控制台。

本地开发管理员为 `admin` / `Admin123456!`；也可以首次启动前通过 `ADMIN_USERNAME`、`ADMIN_PASSWORD` 覆盖。普通用户在 App 注册。该默认密码仅用于本地开发，MySQL 部署配置不提供默认管理员密码。

Android Studio 打开项目根目录后，运行 `app` 到 Android 模拟器。Debug 默认连接 `http://10.0.2.2:8080/api/`。真机需要手机与电脑可互通，在登录页“连接设置”中填写电脑的局域网地址；服务地址不是 MySQL 地址。

也可以通过构建参数指定服务地址：

```powershell
.\gradlew.bat :app:assembleDebug -PapiBaseUrl=http://10.0.2.2:8080/api/
```

Release 仅允许 HTTPS，部署时传入实际域名的 API 地址。当前未配置签名与正式域名，不应将 Debug 安装包当作正式发布版本。

## MySQL 配置

项目提供 `backend/src/main/resources/application-mysql.yml`。需要先创建独立数据库与数据库账号，然后在后端终端设置：

```powershell
$env:SPRING_PROFILES_ACTIVE = 'mysql'
$env:DB_URL = 'jdbc:mysql://localhost:3306/smart_workorder?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
$env:DB_USERNAME = 'smart_workorder'
$env:DB_PASSWORD = '<数据库密码>'
$env:JWT_SECRET = '<至少32字节的随机密钥>'
$env:ADMIN_PASSWORD = '<首次初始化管理员密码>'
mvn spring-boot:run
```

Flyway 在启动时创建业务表，Hibernate 校验表结构。H2 开发数据不会自动复制到 MySQL。不得将真实密钥或密码提交到仓库。

## 开发与验证

```powershell
# Android：在项目根目录执行
.\gradlew.bat :app:assembleDebug :app:lintDebug

# 后端：在 backend 目录执行
mvn test
mvn package
```

`WorkorderJourneyTest` 是连接真实本地后端的模拟器流程测试，会注册验收账号并创建、处理验收工单；适合独立测试数据库。运行前启动后端并确保模拟器地址能访问。

## 智能服务配置

在启动后端的同一终端设置环境变量，然后重启后端：

```powershell
$env:GEMINI_API_KEY = '<你的Gemini密钥>'
$env:GEMINI_MODEL = '<账号中可用、支持结构化输出的Gemini模型名>'
$env:SILICONFLOW_API_KEY = '<你的硅基流动密钥>'
$env:EMBEDDING_MODEL = 'BAAI/bge-m3'
mvn spring-boot:run
```

Gemini 使用原生 REST 协议，默认地址 `https://generativelanguage.googleapis.com/v1beta`；如使用兼容服务，必须保持该协议与路径约定。embedding 默认地址 `https://api.siliconflow.cn/v1`。密钥仅由后端持有，不填入 Android。

未配置智能服务时，App 提供明确的手动填写流程，不返回伪造模型结果。历史工单完成后异步进入知识索引；已有历史记录可由管理员在详情页请求同步。默认索引目录为 `backend/data/vectors/`，不要让多个后端进程同时写入同一目录。

完整环境项见 `backend/.env.example`，该文件只是示例，不会被 Spring Boot 自动加载。
