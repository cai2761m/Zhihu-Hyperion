# 关注用户更新真实响应

2026-10-10，使用已登录 AVD 的生产 `ZhihuApiEnvironment` 采集。

- `recent.json`：`GET https://api.zhihu.com/moments/recent?type=raw`，5 位用户，其中 2 位有未读更新。
- `page0.json` / `page1.json`：现有用户动态接口的连续分页，9 / 7 条真实动态；保留服务端 cursor 和 paging 结构。
- `read.json`：对头像条目的原始 `brief` 调用 `POST /moments/recent/read` 的真实成功响应；再请求 recent 已确认对应用户的未读数为 0。

仅一致性脱敏 people 对象的身份字段及分页 URL 中的用户名；正文、内容 id、类型、计数、动态描述、反馈 brief、分页 cursor 均来自原始响应。原始证据在仓库外的私有目录，夹具不含会话凭据。
