param(
    [string]$BaseUrl = 'http://127.0.0.1:8080/api',
    [string]$AdminUsername = 'admin',
    [string]$AdminPassword = $env:ADMIN_PASSWORD
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrEmpty($AdminPassword)) { $AdminPassword = 'Admin123456!' }
$BaseUrl = $BaseUrl.TrimEnd('/')
function Request-Api {
    param([string]$Method, [string]$Path, $Body, [string]$Token)
    $parameters = @{ Method = $Method; Uri = "$BaseUrl$Path"; ContentType = 'application/json; charset=utf-8' }
    if ($Token) { $parameters.Headers = @{ Authorization = "Bearer $Token" } }
    if ($null -ne $Body) { $parameters.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 8)) }
    Invoke-RestMethod @parameters
}
function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}
$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 12)
$registration = @{ username = "check_$suffix"; displayName = '接口验收用户'; password = [Guid]::NewGuid().ToString('N') }
$account = Request-Api POST '/auth/register' $registration
$admin = Request-Api POST '/auth/login' @{ username = $AdminUsername; password = $AdminPassword }
$body = @{
    clientRequestId = [Guid]::NewGuid().ToString()
    originalDescription = '  三楼打印机一直卡纸，并伴有异响，无法使用。  '
    summary = "打印机故障验收_$suffix"
    category = '设备故障'; priority = '较急'; deviceName = '打印机'; location = '办公室三楼'; tags = @('卡纸', '异响')
}
$created = Request-Api POST '/tickets' $body $account.token
$retry = Request-Api POST '/tickets' $body $account.token
Assert-True ($created.id -eq $retry.id) '重复请求创建了不同工单'
Assert-True ($created.status -eq 'PENDING') '初始状态不正确'
$processing = Request-Api POST "/tickets/$($created.id)/start" $null $admin.token
Assert-True ($processing.status -eq 'PROCESSING') '接单后状态不正确'
$completed = Request-Api POST "/tickets/$($created.id)/complete" @{ resolution = '清理进纸通道，更换搓纸轮，连续打印测试正常。' } $admin.token
Assert-True ($completed.status -eq 'COMPLETED') '完成状态不正确'
$visible = Request-Api GET "/tickets/$($created.id)" $null $account.token
Assert-True ($visible.originalDescription -ceq $body.originalDescription) '用户原始描述未完整保留'
Assert-True ($visible.resolution -like '*连续打印测试正常*') '用户无法查看处理结果'
$stats = Request-Api GET '/statistics' $null $account.token
Assert-True ($stats.total -eq 1 -and $stats.completed -eq 1) '统计结果不正确'
Write-Output "PASS: 注册、登录、重复提交、接单、完成、用户查看结果及统计。工单编号：$($created.number)"
Write-Output '验收脚本创建了实际测试账号和工单，请使用独立开发或测试数据库。'
