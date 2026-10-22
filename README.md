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

先在 Android Studio 底部 Terminal（终端）启动后端（需要 JDK 17 和 Maven）。Windows 推荐使用启动脚本，可从任意目录调用：

```powershell
& 'D:\Savedfiles\AndroidStudioProjects\smartworkordersystem\scripts\Start-Backend.ps1'
```

脚本固定后端 POM 路径，并使用从 POM 读取版本的完整 Spring Boot 插件坐标，避免终端位置或插件前缀解析差异。等日志显示 `Started WorkorderApplication` 后再在 App 注册。保持终端开启；按 Ctrl+C 停止后端。

也可以手动进入后端目录执行：

```powershell
cd D:\Savedfiles\AndroidStudioProjects\smartworkordersystem\backend
mvn spring-boot:run
```

默认 `dev` 模式使用嵌入式 H2 文件数据库，随后端自动启动和关闭，不需要单独启动数据库服务。账号、工单等保存在 `backend/data/workorders.mv.db`，停止或重启后端不会清空。无需先配置或启动 MySQL，不开放 H2 网页控制台。以后切换为 `mysql` profile 时，需另外启动 MySQL 服务或使用已运行的云数据库。

如果手动命令出现 `NoPluginFoundForPrefixException`，表示 Maven 没能解析插件简称，并非数据库未开启。先使用上述脚本，或明确指定 POM 和完整插件坐标：

```powershell
mvn -f 'D:\Savedfiles\AndroidStudioProjects\smartworkordersystem\backend\pom.xml' org.springframework.boot:spring-boot-maven-plugin:3.5.16:run
```

当前 POM 已声明该插件。仅凭错误末尾不能确定是哪个终端配置或下载环节导致；Maven 官方列出的可能原因包括插件名称、仓库、插件组及元数据问题，见 [官方错误说明](https://cwiki.apache.org/confluence/spaces/MAVEN/pages/4589160/NoPluginFoundForPrefixException)。排查时保留错误上方的首次 ERROR / WARNING。

本地开发管理员为 `admin` / `Admin123456!`；也可以首次启动前通过 `ADMIN_USERNAME`、`ADMIN_PASSWORD` 覆盖。普通用户在 App 注册。该默认密码仅用于本地开发，MySQL 部署配置不提供默认管理员密码。

新建工单有两种录入方式：“智能报修”填写问题描述，整理后核对并修改自动填入的字段，再确认提交；“手动填写”直接填工单信息，无需另写智能报修原文。问题摘要必填，类别和紧急程度有默认值；设备、地点、标签、影响范围、故障开始时间、错误提示、已尝试操作均可选。漏填必填项时会在对应字段显示错误并自动定位。

更新本版本后需重启后端，并在 Android Studio 重新运行 App。后端启动时会自动执行 V3 数据库迁移，保留已有工单和原始描述；不需要删除数据库。

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

推荐使用本地配置文件：在项目根目录将 `backend/.env.example` 复制为 `backend/.env`，填写 `GEMINI_API_KEY`、`GEMINI_MODEL`，需要 RAG 时再填写 `SILICONFLOW_API_KEY`。模型名使用你的账号实际支持结构化输出的 Gemini 模型。保留 `SPRING_PROFILES_ACTIVE=dev` 即继续使用本地 H2，不需要配置 MySQL。

```powershell
# 仅首次创建；如果已经有 .env，直接编辑它，不要覆盖。
Copy-Item backend/.env.example backend/.env
# 填写后从项目根目录启动，每次启动都会读取本地配置。
.\scripts\Start-Backend.ps1
```

`.env.example` 随代码上传；真实 `.env`、数据库和个人账号笔记已被 Git 忽略，日常推送无需删除本地配置。脚本支持 `KEY=value` 和成对引号包裹的字面量，不执行变量展开或命令；注释单独一行，空值跳过。当前终端已有的同名环境变量优先，脚本结束后清除本次临时加载的环境变量。

也可以在启动后端的同一终端设置环境变量，再于 `backend` 目录启动：

```powershell
$env:GEMINI_API_KEY = '<你的Gemini密钥>'
$env:GEMINI_MODEL = '<账号中可用、支持结构化输出的Gemini模型名>'
$env:SILICONFLOW_API_KEY = '<你的硅基流动密钥>'
$env:EMBEDDING_MODEL = 'BAAI/bge-m3'
mvn spring-boot:run
```

Gemini 使用原生 REST 协议，默认地址 `https://generativelanguage.googleapis.com/v1beta`；如使用兼容服务，必须保持该协议与路径约定。embedding 默认地址 `https://api.siliconflow.cn/v1`。密钥仅由后端持有，不填入 Android。

未配置智能服务时，App 提供明确的手动填写流程，不返回伪造模型结果。历史工单完成后异步进入知识索引；已有历史记录可由管理员在详情页请求同步。默认索引目录为 `backend/data/vectors/`，不要让多个后端进程同时写入同一目录。

完整环境项见 `backend/.env.example`。只有项目启动脚本会读取 `.env`；直接执行 `mvn spring-boot:run` 时仍需自行在终端设置环境变量。首次真实联调建议先验证 Gemini 的工单回填，再验证 embedding 和历史案例检索。
