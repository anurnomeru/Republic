package ink.anur.pojo.log;

import java.nio.ByteBuffer;
import ink.anur.pojo.common.AbstractStruct;
import ink.anur.pojo.common.RequestTypeEnum;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;

/**
 * Created by Anur IjuoKaruKas on 2019/3/29
 * <p>
 * 用于向协调 Leader 拉取消息，并告知自己的提交进度
 */
public class Fetch extends AbstractStruct {

    public static final int FetchGenerationOffset = AbstractStruct.Companion.getOriginMessageOverhead();

    public static final int FetchGenerationLength = 8;

    public static final int FetchOffsetOffset = FetchGenerationOffset + FetchGenerationLength;

    public static final int FetchOffsetLength = 8;

    public static final int BaseMessageOverhead = FetchOffsetOffset + FetchOffsetLength;

    public Fetch(GenerationAndOffset fetchGAO) {
        init(BaseMessageOverhead, RequestTypeEnum.FETCH, buffer -> {
            buffer.putLong(fetchGAO.getGeneration());
            buffer.putLong(fetchGAO.getOffset());
            return null;
        });
    }

    public Fetch(ByteBuffer byteBuffer) {
        this.setBuffer(byteBuffer);
    }

    public GenerationAndOffset getFetchGAO() {
        return new GenerationAndOffset(this.getBuffer()
                                           .getLong(FetchGenerationOffset), this.getBuffer()
                                                                                .getLong(FetchOffsetOffset));
    }

    @Override
    public void writeIntoChannel(Channel channel) {
        channel.write(Unpooled.wrappedBuffer(this.getBuffer()));
    }

    @Override
    public int totalSize() {
        return size();
    }

    @Override
    public String toString() {
        return "Fetcher{ GAO => " + getFetchGAO().toString() + "}";
    }
}
