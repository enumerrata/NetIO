package net.io.protocal.proto;

import net.dipatch.ISender;
import net.io.BaseMessage;

public class ProtoMessage extends BaseMessage {
	
	/**最大数据读取次数*/
	protected static final int CONTENT_MAX_READ_TIMES = 5;
	
	protected byte[] datas;
	
	public ProtoMessage(int messageId, int status, String sessionId, ISender sender, byte[] datas) {
		super(messageId, status, sessionId, sender);
		mergeFrom(datas);
	}

	@Override
	public byte[] getByteArray() {
		return datas;
	}

	@Override
	public void mergeFrom(byte[] datas) {
		this.datas = datas;
	}

}
