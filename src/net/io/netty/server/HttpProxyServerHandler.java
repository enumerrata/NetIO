package net.io.netty.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.ServerCookieEncoder;
import io.netty.handler.codec.rtsp.RtspHeaders.Values;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.text.MessageFormat;
import java.util.Set;

import net.dipatch.IContent;
import net.dipatch.IDispatchManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpProxyServerHandler<T extends IContent> extends SimpleChannelInboundHandler<Object> {
	
	private static final Logger log = LoggerFactory.getLogger(HttpProxyServerHandler.class);
	
	private static final AttributeKey<NettyHttpSession> SESSION = AttributeKey.valueOf("NettyHttpSession");
	
	private static final String CONTENT_LENGHT = "Content-Length";
	
	private static final String MESSAGE_ID = "messageId";
	
	private static final String COOKIE = "eos_style_cookie=default; JSESSIONID={0}; Path=/{1}/; HttpOnly";
	
	private final String path;
	
	private final IDispatchManager<T> dispatchManager;
	
	private final INettyContentFactory<T> nettyContentFactory;
	
	public HttpProxyServerHandler(String path, IDispatchManager<T> dispatchManager, INettyContentFactory<T> nettyContentFactory) {
		this.path = path;
		this.dispatchManager = dispatchManager;
		this.nettyContentFactory = nettyContentFactory;
	}
	
	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
		Channel channel = ctx.channel();
		if (msg instanceof HttpRequest) { // http请求头
			readHead(channel, (HttpRequest) msg);
		} else if (msg instanceof HttpContent) { // http请求体
			HttpContent httpContent = (HttpContent) msg;
			ByteBuf content = httpContent.content();
			Attribute<NettyHttpSession> session = channel.attr(SESSION);
			NettyHttpSession httpSession = session.get();
			if (httpSession != null) {
				readContent(channel, httpSession, content);
			}
		}
	}
	
	private void readHead(Channel channel, HttpRequest req) {
		HttpHeaders headers = req.headers();
		Attribute<NettyHttpSession> session = channel.attr(SESSION);
		NettyHttpSession httpSession = session.get() ;
		if (httpSession == null) {
			httpSession = new NettyHttpSession(channel.id().asLongText(), HttpHeaders.isKeepAlive(req));
			httpSession.setMessageId(headers.get(MESSAGE_ID));
			session.set(httpSession);
		}
//		httpSession.setCookie(headers.get(SESSION_ID));
		httpSession.setContentLength(Integer.parseInt(headers.get(CONTENT_LENGHT)));
	}
	
	private void readContent(Channel channel, NettyHttpSession httpSession, ByteBuf content) {
//		SocketAddress address = channel.remoteAddress();
		int contentLength = httpSession.getContentLength();
//		String messageId = httpSession.getMessageId();
		byte[] datas = new byte[contentLength < 0 ? 0 : contentLength];
		content.readBytes(datas);
		String sessionId = httpSession.getId();
		log.debug("sessionId : {}", sessionId);
		
		T nettyContent = nettyContentFactory.createContent(channel, httpSession, content);
		dispatchManager.addDispatch(nettyContent);
		
		Set<Cookie> cookies = CookieDecoder.decode(MessageFormat.format(COOKIE, sessionId, path));
		FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(datas));
		HttpHeaders headers = response.headers();
		for (Cookie cookie : cookies) {
			headers.add(HttpHeaders.Names.SET_COOKIE, ServerCookieEncoder.encode(cookie));
		}
        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/plain");
        response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, response.content().readableBytes());
        if (httpSession.isKeepAlive()) {
            response.headers().set(HttpHeaders.Names.CONNECTION, Values.KEEP_ALIVE);
        }
		channel.writeAndFlush(response);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		log.info("[Coming Out]IP:{}", ctx.channel().remoteAddress());
		ctx.channel().close();
		ctx.close();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		log.info("[Coming Out Error]IP:{} ; Error:{}", ctx.channel().remoteAddress(), cause.getMessage());
		ctx.channel().close();
		ctx.close();
	}
	
	private static class NettyHttpSession implements INettyHttpSession {
		
		private int contentLength;
		
		private String messageId;
		
		private final String id;
		
		private final boolean isKeepAlive;
		
		public NettyHttpSession(String id, boolean isKeepAlive) {
			this.id = id;
			this.isKeepAlive = isKeepAlive;
		}

		@Override
		public int getContentLength() {
			return contentLength;
		}

		public void setContentLength(int contentLength) {
			this.contentLength = contentLength;
		}

		@Override
		public String getId() {
			return id;
		}

		@Override
		public String getMessageId() {
			return messageId;
		}

		public void setMessageId(String messageId) {
			this.messageId = messageId;
		}

		@Override
		public boolean isKeepAlive() {
			return isKeepAlive;
		}
		
	}

}