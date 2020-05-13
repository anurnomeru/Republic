#### Fetch 请求的发起

Fetch 请求是由 FetchMeta （位于 **FetchService**） 所发起，FetchMeta 是对定时任务的一个封装
 - 它首先是一个定时任务
 - 支持手动触发定时任务，并重置计时
 - 支持阻塞等待任务成功或失败
 - 支持取消，成功功能，取消或者成功后，定时任务不再执行

我们新建一个 FetchMeta，指定从哪个服务，哪个进度开始 fetch，fetch 到什么进度为止，如果没有指定 fetch 到什么进度，则会无限进行 fetch。

Fetch 请求的组成十分简单，只需要附带上自己想要 fetch 的进度 offset 即可

#### Fetch 请求的处理

Fetch 请求的处理由 FetchHandlerService 处理，这里不过多赘述。

#### Fetch 的结果也就是 FetchResponse 的处理

FetchResponse 由 FetchResponseHandlerService 处理，实际上交回 **FetchService** 进行处理。

处理会将结果进行入库，如果 fetch 到了预计进度，则会触发 complete 。