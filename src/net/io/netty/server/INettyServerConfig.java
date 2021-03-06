package net.io.netty.server;

import net.io.dispatch.IContentFactory;
import net.io.dispatch.IDispatchManager;

public interface INettyServerConfig {

	IDispatchManager getDispatchManager();

	IContentFactory getNettyContentFactory();

	int getParentThreadNum();

	int getChildThreadNum();

	int getPort();

	String getIp();

	long getReaderIdleTime();

	long getWriterIdleTime();

	long getAllIdleTime();

	int getSoBacklog();

}
