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

已实现注册登录、两个角色首页、文字工单、确认提交、列表搜索与筛选、详情、接单、完成、处理结果查询、基础统计；还包括 OpenAI 兼容 LLM 分析预览、硅基流动 embedding、历史工单 Lucene 向量检索和处理参考建议。

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

推荐使用本地配置文件：首次在项目根目录将 `backend/.env.example` 复制为 `backend/.env`，填写 `LLM_BASE_URL`、`LLM_API_KEY`、`LLM_MODEL`，需要 RAG 时再填写 `SILICONFLOW_API_KEY`。模型名使用服务商实际提供的模型 ID，可包含厂商前缀、斜杠或冒号。保留 `SPRING_PROFILES_ACTIVE=dev` 即继续使用本地 H2，不需要配置 MySQL。

```dotenv
LLM_BASE_URL=https://你的中转站域名/v1
LLM_API_KEY=填写该服务商提供的密钥
LLM_MODEL=填写该服务商提供的模型ID
LLM_RESPONSE_FORMAT=json_object
```

`LLM_BASE_URL` 包含服务商要求的版本路径，后端在其后添加 `/chat/completions`；也支持填写完整的 Chat Completions 地址（不会重复追加）。必须是 OpenAI 兼容的 Chat Completions 服务，不能直接填写 Gemini 原生 generateContent、Responses 或其他协议的地址。更换服务商时需配套修改地址、密钥和模型名。

| LLM_RESPONSE_FORMAT | 使用场景 |
| --- | --- |
| json_object | 默认，发送 JSON 对象输出约束；后端继续校验字段类型、枚举与长度 |
| json_schema | 服务商支持严格结构化输出时使用，发送标准 JSON Schema |
| text | 服务商不支持 response_format 时使用，不发送该参数；通过提示词要求 JSON，后端仍严格校验，可去除包裹整个 JSON 的单个代码块 |

不会在参数不支持时自动重复请求，以免重复消耗额度。若服务商拒绝输出格式参数，可按其文档切换为 `text`；模型仍需能返回符合字段要求的 JSON。

```powershell
# 仅首次创建；如果已经有 .env，直接编辑它，不要覆盖。
Copy-Item backend/.env.example backend/.env
# 填写后从项目根目录启动，每次启动都会读取本地配置。
.\scripts\Start-Backend.ps1
```

`.env.example` 随代码上传；真实 `.env`、数据库和个人账号笔记已被 Git 忽略，日常推送无需删除本地配置。脚本支持 `KEY=value` 和成对引号包裹的字面量，不执行变量展开或命令；注释单独一行，空值跳过。当前终端已有的同名环境变量优先，脚本结束后清除本次临时加载的环境变量。

也可以在启动后端的同一终端设置环境变量，再于 `backend` 目录启动：

```powershell
$env:LLM_BASE_URL = 'https://你的中转站域名/v1'
$env:LLM_API_KEY = '<服务商密钥>'
$env:LLM_MODEL = '<服务商提供的模型ID>'
$env:LLM_RESPONSE_FORMAT = 'json_object'
$env:SILICONFLOW_API_KEY = '<你的硅基流动密钥>'
$env:EMBEDDING_MODEL = 'BAAI/bge-m3'
mvn spring-boot:run
```

LLM 使用 `Authorization: Bearer`、`messages`、`model` 的 OpenAI 兼容 Chat Completions 请求，默认基础地址为 `https://api.openai.com/v1`，模型名与密钥不预设；可更换为提供该协议的服务商或中转站。embedding 已使用兼容 `/embeddings` 协议，默认地址仍为 `https://api.siliconflow.cn/v1`，模型为 `BAAI/bge-m3`。密钥仅由后端持有，不填入 Android。

从旧版更新时，`GEMINI_*` 不再生效，改填新的 `LLM_*`。现有本地配置会保留，不自动将旧密钥发送给新地址。新智能工单来源标记为 LLM，历史 GEMINI 标记继续正常展示，无需删除数据库。

未配置智能服务时，App 提供明确的手动填写流程，不返回伪造模型结果。历史工单完成后异步进入知识索引；已有历史记录可由管理员在详情页请求同步。默认索引目录为 `backend/data/vectors/`，不要让多个后端进程同时写入同一目录。

完整环境项见 `backend/.env.example`。只有项目启动脚本会读取 `.env`；直接执行 `mvn spring-boot:run` 时仍需自行在终端设置环境变量。首次真实联调建议先验证 LLM 的工单回填，再验证 embedding 和历史案例检索。协议参考 [官方 Chat Completions 接口](https://developers.openai.com/api/reference/resources/chat) 与 [结构化输出说明](https://developers.openai.com/api/docs/guides/structured-outputs)，第三方服务的实际支持范围仍需用其文档和账号联调确认。
