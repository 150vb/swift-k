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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Adler32;

import org.apache.log4j.Logger;
import org.globus.cog.karajan.workflow.service.RemoteConfiguration;
import org.globus.cog.karajan.workflow.service.RequestManager;
import org.globus.cog.karajan.workflow.service.commands.ChannelConfigurationCommand;

public abstract class AbstractStreamKarajanChannel extends AbstractKarajanChannel implements
		Purgeable {
	public static final Logger logger = Logger.getLogger(AbstractStreamKarajanChannel.class);

	public static final int STATE_IDLE = 0;
	public static final int STATE_RECEIVING_DATA = 1;

	public static final int HEADER_LEN = 20;

	private InputStream inputStream;
	private OutputStream outputStream;
	private URI contact;
	private final byte[] rhdr = new byte[HEADER_LEN];
	private final ByteBuffer bhdr = ByteBuffer.wrap(rhdr);
	private byte[] data;
	private ByteBuffer bdata;
	private int dataPointer;
	private int state, tag, flags, len, hcsum, csum;

	protected AbstractStreamKarajanChannel(RequestManager requestManager,
			ChannelContext channelContext, boolean client) {
		super(requestManager, channelContext, client);
	}

	protected InputStream getInputStream() {
		return inputStream;
	}

	protected void setInputStream(InputStream inputStream) {
		this.inputStream = inputStream;
	}

	protected OutputStream getOutputStream() {
		return outputStream;
	}

	protected void setOutputStream(OutputStream outputStream) {
		this.outputStream = outputStream;
	}

	public URI getContact() {
		return contact;
	}

	public void setContact(URI contact) {
		this.contact = contact;
	}

	protected abstract void reconnect() throws ChannelException;

	protected synchronized boolean handleChannelException(Exception e) {
		logger.info("Channel config: " + getChannelContext().getConfiguration());
		if (!ChannelManager.getManager().handleChannelException(this, e)) {
			close();
			return false;
		}
		else {
		    return true;
		}
	}

	protected void configure() throws Exception {
		URI callbackURI = null;
		ChannelContext sc = getChannelContext();
		if (sc.getConfiguration().hasOption(RemoteConfiguration.CALLBACK)) {
			callbackURI = getCallbackURI();
		}
		// String remoteID = sc.getChannelID().getRemoteID();

		ChannelConfigurationCommand ccc = new ChannelConfigurationCommand(sc.getConfiguration(),
				callbackURI);
		ccc.execute(this);
		logger.info("Channel configured");
	}

	public synchronized void sendTaggedData(int tag, int flags, byte[] data, SendCallback cb) {
		if (getNIOChannel() != null) {
			getNIOSender(this).enqueue(tag, flags, data, this, cb);
		}
		else {
			getSender(this).enqueue(tag, flags, data, this, cb);
		}
	}

	static long cnt;

	static long savail;

	protected boolean step() throws IOException {
		int avail = inputStream.available();
		savail += avail;
		cnt++;
		if (avail == 0) {
			return false;
		}
		// we can only rely on GsiInputStream.available() returning 0 if nothing
		// is available
		// see https://bugzilla.mcs.anl.gov/globus/show_bug.cgi?id=6747
		boolean any = false;
		if (state == STATE_IDLE) {
			dataPointer = readFromStream(inputStream, rhdr, dataPointer);
			if (dataPointer == HEADER_LEN) {
				tag = unpack(rhdr, 0);
				flags = unpack(rhdr, 4);
				len = unpack(rhdr, 8);
				hcsum = unpack(rhdr, 12);
				if ((tag ^ flags ^ len) != hcsum) {
					logger.warn("Header checksum failed. Computed checksum: " + 
							Integer.toHexString(tag ^ flags ^ len) + 
							", checksum: " + Integer.toHexString(hcsum));
					return true;
				}
				csum = unpack(rhdr, 16);
				if (len > 1048576) {
					logger.warn("Big len: " + len + " (tag: " + tag + ", flags: " + flags + ")");
					data = new byte[1024];
					inputStream.read(data);
					logger.warn("data: " + ppByteBuf(data));
					return true;
				}
				data = new byte[len];
				dataPointer = 0;
				state = STATE_RECEIVING_DATA;
				avail = inputStream.available();
				any = true;
			}
		}
		if (state == STATE_RECEIVING_DATA) {
			while (avail > 0 && dataPointer < len) {
				any = true;
				dataPointer = readFromStream(inputStream, data, dataPointer);
				avail = inputStream.available();
			}
			if (dataPointer == len) {
				dataPointer = 0;
				state = STATE_IDLE;
				
				if (csum != 0) {
					Adler32 c = new Adler32();
					c.update(data);
					
					if (((int) c.getValue()) != csum) {
						logger.warn("Data checksum failed. Compute checksum: " + 
								Integer.toHexString((int) c.getValue()) + ", checksum: " + Integer.toHexString(csum));
					}
				}
				byte[] tdata = data;
				// don't hold reference from the channel to the data
				data = null;
				if (flagIsSet(flags, REPLY_FLAG)) {
					// reply
					handleReply(tag, flags, len, tdata);
				}
				else {
					// request
					handleRequest(tag, flags, len, tdata);
				}
				data = null;
			}
		}
		return any;
	}
	
	protected void stepNIO() throws IOException {
		ReadableByteChannel channel = (ReadableByteChannel) getNIOChannel();
		if (state == STATE_IDLE) {
			readFromChannel(channel, bhdr);
			if (!bhdr.hasRemaining()) {
				tag = unpack(rhdr, 0);
				flags = unpack(rhdr, 4);
				len = unpack(rhdr, 8);
				hcsum = unpack(rhdr, 12);
				if ((tag ^ flags ^ len) != hcsum) {
					logger.warn("(NIO) Header checksum failed. Computed checksum: " + 
							Integer.toHexString(tag ^ flags ^ len) + 
							", checksum: " + Integer.toHexString(hcsum));
					logger.warn("Tag: " + tag + ", flags: " + flags + ", len: " + len + ", data: " + ppByteBuf(rhdr));
					System.exit(3);
					return;
				}
				csum = unpack(rhdr, 16);
				if (len > 1048576) {
					logger.warn("Big len: " + len + " (tag: " + tag + ", flags: " + flags + ")");
					bdata = ByteBuffer.wrap(data = new byte[1024]);
					readFromChannel(channel, bdata);
					logger.warn("data: " + ppByteBuf(data));
					return;
				}
				bdata = ByteBuffer.wrap(data = new byte[len]);
				state = STATE_RECEIVING_DATA;
				bhdr.rewind();
			}
		}
		if (state == STATE_RECEIVING_DATA) {
			readFromChannel(channel, bdata);
			
			if (!bdata.hasRemaining()) {
				state = STATE_IDLE;
				
				if (csum != 0) {
					Adler32 c = new Adler32();
					c.update(data);
					
					if (((int) c.getValue()) != csum) {
						logger.warn("Data checksum failed. Compute checksum: " + 
								Integer.toHexString((int) c.getValue()) + ", checksum: " + Integer.toHexString(csum));
					}
				}
				byte[] tdata = data;
				// don't hold reference from the channel to the data
				data = null; bdata = null;
				if (flagIsSet(flags, REPLY_FLAG)) {
					// reply
					handleReply(tag, flags, len, tdata);
				}
				else {
					// request
					handleRequest(tag, flags, len, tdata);
				}
			}
		}
	}
	
	protected void readFromChannel(ReadableByteChannel c, ByteBuffer buf) throws IOException {
		if (AbstractTCPChannel.logPerformanceData) {
			PerformanceDiagnosticInputStream.bytesRead(c.read(buf));
		}
		else {
			c.read(buf);
		}
	}

	public void purge(KarajanChannel channel) throws IOException {
		getSender(this).purge(this, channel);
	}

	protected void register() {
		if  (getNIOChannel() != null) {
			getNIOMultiplexer().register(this);
		}
		else {
			getMultiplexer(FAST).register(this);
		}
	}

	protected void unregister() {
		if  (getNIOChannel() != null) {
			getNIOMultiplexer().unregister(this);
		}
		else {
			getMultiplexer(FAST).unregister(this);
		}
	}

	public void flush() throws IOException {
		outputStream.flush();
	}

	private static Map<Class<? extends KarajanChannel>, Sender> sender;

	private static synchronized Sender getSender(KarajanChannel channel) {
		if (sender == null) {
			sender = 
				new HashMap<Class<? extends KarajanChannel>, Sender>();
		}

		Sender s = sender.get(channel.getClass());
		if (s == null) {
			if (logger.isInfoEnabled()) {
				logger.info("Using threaded sender for " + channel);
			}
			sender.put(channel.getClass(), s = new Sender(channel.getClass().getSimpleName()));
			s.start();
		}
		return s;
	}
	
	private static NIOSender nioSender;
	
	private static synchronized NIOSender getNIOSender(KarajanChannel channel) {
		if (nioSender == null) {
			if (logger.isInfoEnabled()) {
				logger.info("Using NIO sender for " + channel);
			}
			nioSender = new NIOSender();
			nioSender.start();
		}

		return nioSender;
	}

	private static final int MUX_COUNT = 2;
	static Multiplexer[] multiplexer;
	public static final int FAST = 0;
	public static final int SLOW = 1;

	public static synchronized Multiplexer getMultiplexer(int n) {
		if (multiplexer == null) {
			multiplexer = new Multiplexer[MUX_COUNT];
			for (int i = 0; i < MUX_COUNT; i++) {
				multiplexer[i] = new Multiplexer(i);
				multiplexer[i].start();
			}
		}
		return multiplexer[n];
	}

	private static NIOMultiplexer nioMultiplexer;
	
	private static synchronized NIOMultiplexer getNIOMultiplexer() {
		if (nioMultiplexer == null) {
			nioMultiplexer = new NIOMultiplexer();
			nioMultiplexer.start();
		}
		
		return nioMultiplexer;
	}
}
