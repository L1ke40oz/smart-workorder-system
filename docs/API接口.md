# REST API 与联调约定

基础路径：`/api`。请求与响应使用 UTF-8 JSON。除注册、登录和健康检查外，均需 `Authorization: Bearer <token>`。业务失败统一返回 `{"message":"可读的中文错误信息"}`。

## 身份接口

| 方法 | 路径 | 请求 / 响应 |
| --- | --- | --- |
| POST | `/auth/register` | 输入 username、displayName、password；201 返回登录会话 |
| POST | `/auth/login` | 输入 username、password；200 返回登录会话 |
| GET | `/auth/me` | 返回 id、username、displayName、role |
| GET | `/health` | 返回 `{"status":"UP"}` |

账号统一为小写，注册接受 4～32 位 ASCII 字母、数字和下划线。密码 8～64 字符且 UTF-8 编码不超过 72 字节。注册不接受 role 等额外字段。会话包含 token、expiresAt、user，令牌有效期 12 小时。

## 工单接口

| 方法 | 路径 | 作用 / 权限 |
| --- | --- | --- |
| POST | `/tickets` | 普通用户确认后创建工单 |
| GET | `/tickets` | 用户查询本人工单；管理员查询全部工单 |
| GET | `/tickets/{id}` | 查看可访问工单详情 |
| POST | `/tickets/{id}/start` | 管理员接单，PENDING → PROCESSING |
| POST | `/tickets/{id}/complete` | 接单管理员填写结果并完成 |
| GET | `/statistics` | 返回当前角色可见范围内的总数、各状态数量和类别数量 |

手动创建示例（不需要 originalDescription 和 analysisId）：

```json
{
  "clientRequestId": "54aa5e9a-a24f-4cf0-9afc-a5c7d05d0a68",
  "summary": "打印机频繁卡纸并伴随异响",
  "category": "设备故障",
  "priority": "较急",
  "deviceName": "打印机",
  "location": "三楼",
  "tags": ["卡纸", "异响"],
  "impactScope": "同办公室 5 人",
  "faultStartedAt": "今天上午",
  "errorMessage": "E03",
  "attemptedActions": "重启后仍卡纸"
}
```

通过智能分析创建时必须同时携带返回的 `analysisId` 和对应的完整 `originalDescription`。该 ID 绑定当前用户和原始描述，30 分钟内有效，仅允许创建一张正式工单。客户端可核对和修正结构化字段；修改原文后需重新分析，也可切换到手动填写。`analysisSource` 由后端决定，不能由客户端伪造。

手动创建不要求独立原文，缺省 / null / 全空白原文持久化为 null；若旧客户端仍提交非空原文，按输入原样保留。Android 手动模式不发送智能输入框内的草稿及 analysisId，来源为 MANUAL；新智能工单来源为 LLM。历史 GEMINI 来源保留，仍按智能工单展示并兼容同内容重试。手动详情隐藏空的原始描述区域，不拼接或伪造原文。

| 字段 | 约束 |
| --- | --- |
| summary | 必填，最多 120 字符 |
| category / priority | 必填，固定枚举；Android 提供默认值 |
| originalDescription | 手动可省略；智能必须与预览一致，最多 5000 字符 |
| deviceName / location | 选填，各最多 100 字符 |
| tags | 必传数组，可为空，最多 8 项，每项非空且不超过 24 字符 |
| impactScope | 选填，最多 100 字符，影响人员或区域 |
| faultStartedAt | 选填，最多 100 字符，原文中的故障开始时间描述，例如“今天上午”；不是 ISO 时间戳，不等同于管理员接单时间 startedAt |
| errorMessage | 选填，最多 500 字符，错误提示或错误码 |
| attemptedActions | 选填，最多 1000 字符，用户已尝试的操作和效果 |

`clientRequestId` 由客户端为本次提交生成。网络失败后的同内容重试复用同一 ID，服务端返回已创建记录；同一标识携带不同内容返回 409。正常创建和幂等重试均返回 201。

列表支持：

| 参数 | 约束 |
| --- | --- |
| status | 可省略；PENDING / PROCESSING / COMPLETED |
| category | 可省略；SPEC 的五种中文类别 |
| query | 可省略；匹配摘要或编号，最多 100 字符 |
| page | 从 0 开始，默认 0 |
| size | 1～50，默认 20 |

列表响应：`items`、`total`、`page`、`size`、`totalPages`。详情包含原始描述、结构化字段、来源、状态、提交人、处理人、实际结果和关键时间。普通用户访问他人工单返回 404。

完成请求：`{"resolution":"实际处理经过与验证结果"}`。不能为空白，最多 5000 字符；不接受跳过接单、重复完成或其他管理员代为完成。

## 智能受理与知识辅助

| 方法 | 路径 | 作用 / 权限 |
| --- | --- | --- |
| GET | `/capabilities` | 返回 analysis、rag 配置可用标识，不返回密钥；标识为 true 不等于外部网络或账号已验证 |
| POST | `/analyses` | 普通用户提交 originalDescription，后端调用 OpenAI 兼容 LLM 并暂存分析结果 |
| GET | `/tickets/{id}/suggestions` | 管理员检索历史已完成案例并获取处理参考 |
| POST | `/knowledge/sync` | 管理员请求异步同步历史工单，202 返回安排信息 |

分析响应：analysisId、originalDescription、result、expiresAt。result 包含 category、deviceName、location、priority、tags、summary、impactScope、faultStartedAt、errorMessage、attemptedActions，严格校验字段类型、枚举与长度。全部键必须返回；选填信息未提及则为 null，不编造具体时间，也不把模型建议当成用户已经采取的操作。

RAG 响应包含：

- `matches`：相关已完成工单，包含 ticketId、number、summary、resolution、similarity。
- `suggestion`：LLM 生成的参考文本，可以为空。
- `suggestionError`：无匹配或建议未成功生成时的说明。
- `referenceNumbers`：建议引用的实际工单编号，仅允许来自本次检索结果。

similarity 使用余弦相似度，不是故障诊断正确率。仅返回高于配置阈值的 Top-3 案例，并排除当前工单；无案例时不调用模型编造建议。建议生成失败时仍返回已经检索到的案例，知识服务不改变工单状态。

## 后端到模型服务的协议

Android 到业务后端的接口保持原有契约，不直接持有服务商密钥。工单整理和 RAG 建议统一调用 `LLM_BASE_URL` 对应的 OpenAI 兼容 Chat Completions 接口：Bearer 鉴权，body 含 model、system / user messages 与 stream=false；读取 choices[0].message.content，仅接受 finish_reason=stop 的完整结果，拒绝 refusal 与工具调用结果。配置基础地址时自动追加 `/chat/completions`，配置完整端点时直接使用。

`LLM_RESPONSE_FORMAT` 支持 json_object（默认）、json_schema（严格标准 Schema）、text（不发送 response_format）。三种模式均要求单个 JSON 对象并执行后端字段校验；不接受多段 JSON、尾随文字或截断结果。text 模式仅允许去掉完整包裹 JSON 的单个代码块，不从任意解释文字中猜测提取内容。不自动切换模式或重试，以免额外消耗服务额度。

Schema 使用小写类型、允许 null 的联合类型、全部键 required 与 additionalProperties=false；服务商是否支持具体输出模式需按其文档选择。模型 ID 原样放入 body，可含斜杠、冒号；不拼到 URL 中。旧 GEMINI_* 配置不再读取。Embedding 已采用兼容 `/embeddings` 请求，维持独立的模型、地址和硅基流动密钥配置。

## 错误状态

| 状态码 | 常见原因 |
| --- | --- |
| 400 | 输入、枚举、分页或 JSON 不合法 |
| 401 | 未登录、账号密码不正确或 JWT 已失效 |
| 403 | 角色不符或不是接单管理员 |
| 404 | 工单 / 分析结果不存在或对当前用户不可见 |
| 409 | 状态冲突、重复账号、分析过期 / 已使用、幂等内容冲突 |
| 502 | 智能服务响应错误、鉴权 / 地址 / 参数配置错误、无效模型结构或异常向量 |
| 503 | 智能服务未配置、限流或知识索引不可用 |
| 504 | 智能服务超时 |

## 可重复的人工联调

启动独立开发 / 测试后端后，在项目根目录执行：

```powershell
.\scripts\Verify-Api.ps1
# 或指定独立测试服务
.\scripts\Verify-Api.ps1 -BaseUrl http://127.0.0.1:8081/api
```

脚本使用本地默认管理员，或读取当前终端的 ADMIN_PASSWORD；会创建真正的验收账号和工单。它不展示密码和令牌，适合用来验证后续 MySQL / VPS 部署的基础接口。
