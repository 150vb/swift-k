//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Jul 21, 2006
 */
package org.globus.cog.karajan.workflow.service.channels;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import org.apache.log4j.Logger;
import org.globus.cog.karajan.workflow.service.RequestManager;
import org.globus.cog.karajan.workflow.service.ServiceContext;
import org.globus.cog.karajan.workflow.service.UDPService;

public class UDPChannel extends AbstractKarajanChannel {
	public static final Logger logger = Logger.getLogger(UDPChannel.class);

	public static final int BUFFER_SIZE = 2048;
	public static final int MAX_SEQ_NUM = 16;
	private URI contact;
	private InetAddress addr;
	private int port;
	private DatagramSocket ds;
	private WeakReference<byte[]> sndbuf;
	private ServiceContext sc;
	private UDPService service;
	private Map<Integer, Integer> tagSeq;
	private boolean started;

	public UDPChannel(DatagramSocket ds, ChannelContext context, RequestManager rm,
			UDPService service, InetSocketAddress addr) {
		super(rm, context, false);
		this.ds = ds;
		this.service = service;
		this.addr = addr.getAddress();
		this.port = addr.getPort() + 1;
		this.contact = service.getContact();
		tagSeq = new HashMap<Integer, Integer>();
	}

	public UDPChannel(URI contact, ChannelContext context, RequestManager rm) {
		super(rm, context, true);
		this.contact = contact;
	}

	public String toString() {
		return "SC-" + contact;
	}

	public boolean isOffline() {
		return false;
	}

	public void start() throws ChannelException {
		try {
			if (ds != null) {
				contact = new URI("udp", null, InetAddress.getLocalHost().getHostAddress(),
						ds.getLocalPort(), null, null, null);
			}
			else {
				ds = new DatagramSocket();
				addr = InetAddress.getByName(contact.getHost());
				port = contact.getPort();
			}
			started = true;
		}
		catch (Exception e) {
			throw new ChannelException("Failed to start UDP channel", e);
		}
	}

	public void sendTaggedData(int tag, int flags, byte[] bytes, SendCallback cb) {
		try {
			if (bytes.length + HDRLEN > BUFFER_SIZE) {
				throw new ChannelIOException("Message too large");
			}
			byte[] buf = null;
			synchronized (this) {
				if (sndbuf != null) {
					buf = sndbuf.get();
					if (buf != null && buf.length < bytes.length + HDRLEN) {
						buf = null;
					}
				}
				if (buf == null) {
					buf = new byte[bytes.length + HDRLEN];
					sndbuf = new WeakReference<byte[]>(buf);
				}
			}
			// this isn't optimal
			System.arraycopy(bytes, 0, buf, HDRLEN, bytes.length);
			pack(buf, 4, tag);
			pack(buf, 8, getSeq(tag, flags));
			pack(buf, 12, flags);
			pack(buf, 0, checksum(buf, 4, bytes.length + HDRLEN - 4));
			DatagramPacket dp = new DatagramPacket(buf, bytes.length + HDRLEN, addr, port);
			ds.send(dp);
			if (cb != null) {
			    cb.dataSent();
			}
		}
		catch (IOException e) {
			throw new ChannelIOException(e);
		}
	}
	
	private static final Integer ZERO = new Integer(0);
	
	private synchronized int getSeq(int tag, int flags) {
		Integer seq = tagSeq.get(tag);
		if (seq == null) {
			seq = ZERO; 
		}
		if ((flags & FINAL_FLAG) != 0) {
			tagSeq.remove(tag);
		}
		else {
			tagSeq.put(tag, seq.intValue() + 1);
		}
		return seq;
	}

	private int checksum(byte[] buf, int offset, int len) {
		Checksum cs = new CRC32();
		cs.update(buf, offset, len);
		return (int) cs.getValue();
	}

	public static final int HDRLEN = 16;

	public void dataReceived(byte[] recvbuf) throws ChannelException {
		int len = recvbuf.length;
		int checksum = unpack(recvbuf, 0);
		int tag = unpack(recvbuf, 4);
		int seq = unpack(recvbuf, 8);
		if (seq > MAX_SEQ_NUM) {
			throw new ChannelException("Sequence number greater than the maximum fragment number ("
					+ MAX_SEQ_NUM + ")");
		}
		int flags = unpack(recvbuf, 12);
		int actual = checksum(recvbuf, 4, len - 4);
		if (checksum != actual) {
			throw new ChannelException("Checksum failed. Expected " + checksum + " got " + actual);
		}
		byte[] data = new byte[len - HDRLEN];
		System.arraycopy(recvbuf, HDRLEN, data, 0, len - HDRLEN);
		if ((flags & REPLY_FLAG) != 0) {
			// reply
			handleReply(tag, flags, len - HDRLEN, data);
		}
		else {
			handleRequest(tag, flags, len - HDRLEN, data);
		}
	}
	
	protected void unregisteredSender(int tag) {
		if (logger.isInfoEnabled()) {
			logger.info(getName() + " Recieved reply to unregistered sender. Tag: " + tag);
		}
	}

	public void shutdown() {
		if (logger.isDebugEnabled()) {
			logger.debug("Shutting down channel " + this);
		}
		super.shutdown();
		service.channelShutDown(this);
	}

	protected void ensureCallbackServiceStarted() throws Exception {
		setCallbackService(service);
		sc = new ServiceContext(service);
	}

	public URI getContact() {
		return contact;
	}

	public ServiceContext getContext() {
		return sc;
	}

	public boolean isRestricted() {
		return false;
	}

	public InetSocketAddress getRemoteAddress() {
		return new InetSocketAddress(addr, port);
	}

	public boolean isStarted() {
		return started;
	}
}
