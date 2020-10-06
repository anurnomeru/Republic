//package ink.anur.io.common.handler
//
//import ink.anur.common.struct.KanashiNode
//import ink.anur.config.InetConfiguration
//import ink.anur.inject.bean.Nigate
//import ink.anur.inject.bean.NigateInject
//import ink.anur.io.common.channel.ChannelService
//import ink.anur.pojo.Register
//import io.netty.channel.Channel
//import io.netty.channel.ChannelHandlerContext
//import io.netty.channel.ChannelInboundHandlerAdapter
//import org.slf4j.LoggerFactory
//
///**
// * Created by Anur IjuoKaruKas on 2020/2/25
// *
// * 自动注册的处理器
// */
//class AutoRegistryHandler(private val node: KanashiNode, private val registrySign: Long,
//                          private val injectChannel: (Channel) -> Unit
//) : ChannelInboundHandlerAdapter() {
//
//    private val logger = LoggerFactory.getLogger(this::class.java)
//
//    @NigateInject
//    private lateinit var inetConfiguration: InetConfiguration
//
//    @NigateInject
//    private lateinit var channelService: ChannelService
//
//    @NigateInject
//    private lateinit var msgCenterService: MsgProcessCentreService
//
//    init {
//        Nigate.injectOnly(this)
//    }
//
//    override fun channelActive(ctx: ChannelHandlerContext?) {
//        super.channelActive(ctx)
//        injectChannel.invoke(ctx!!.channel())
//        logger.info("正在向节点 $node 发送注册请求")
//        channelService.register(node, ctx.channel())
//
//        val register = Register(inetConfiguration.localServerName, registrySign)
//        // TODO 这里可能有bug 如果server处理失败这里将无法连接
//        msgCenterService.sendAsync(node.serverName, register)
//    }
//
//    override fun channelInactive(ctx: ChannelHandlerContext?) {
//        super.channelInactive(ctx)
//        channelService.unRegister(node.serverName)
//        logger.info("与节点 $node 的连接已断开")
//        ctx?.close()
//    }
//}