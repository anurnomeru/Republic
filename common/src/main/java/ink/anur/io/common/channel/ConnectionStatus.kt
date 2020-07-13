package ink.anur.io.common.channel

/**
 * Created by Anur on 2020/7/13
 *
 * 如果要发起一个连接，只需发送 SYN 即可，本节点状态更改为 CONNECTING，等待来自对方的任意消息
 *
 * 如果收到了 SYN，更改自己状态为 ESTABLISHED，并回复 ACK
 * 「不可能在非 CONNECTING 状态下收到非 SYN 的消息，如果收到了，则抛出异常」
 *
 * 当节点在 CONNECTING 状态下收到了任意消息，则将状态修改为 ESTABLISHED
 *
 * <p>
 *
 * 另，关于双方都发起 SYN，建立起不同 channel 的情况。则以更新的连接发起方的 channel 为准，即，要记录自己的发起连接时间。
 */
enum class ConnectionStatus {

    /**
     * 这种状态下，不可主动向其他节点发送消息（保证 SYN 消息先抵达）
     */
    CONNECTING,

    /**
     * 表示连接已经成功建立
     */
    ESTABLISHED,
}