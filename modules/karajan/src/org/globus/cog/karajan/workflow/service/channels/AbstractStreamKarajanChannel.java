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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.globus.cog.karajan.workflow.service.RemoteConfiguration;
import org.globus.cog.karajan.workflow.service.RequestManager;
import org.globus.cog.karajan.workflow.service.commands.ChannelConfigurationCommand;

public abstract class AbstractStreamKarajanChannel extends AbstractKarajanChannel implements
		Purgeable {
	public static final Logger logger = Logger.getLogger(AbstractStreamKarajanChannel.class);

	public static final int STATE_IDLE = 0;
	public static final int STATE_RECEIVING_DATA = 1;

	public static final int HEADER_LEN = 12;

	private InputStream inputStream;
	private OutputStream outputStream;
	private URI contact;
	private final byte[] rhdr;
	private byte[] data;
	private int dataPointer;
	private int state, tag, flags, len;

	protected AbstractStreamKarajanChannel(RequestManager requestManager,
			ChannelContext channelContext, boolean client) {
		super(requestManager, channelContext, client);
		rhdr = new byte[HEADER_LEN];
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
	
	protected synchronized void handleChannelException(Exception e) {
		logger.info("Channel config: " + getChannelContext().getConfiguration());
		ChannelManager.getManager().handleChannelException(this, e);
		try {
			getSender().purge(this, new NullChannel(true));
		}
		catch (IOException e1) {
			logger.warn("Failed to purge queued messages", e1);
		}
	}

	protected void configure() throws Exception {
		URI callbackURI = null;
		ChannelContext sc = getChannelContext();
		if (sc.getConfiguration().hasOption(RemoteConfiguration.CALLBACK)) {
			callbackURI = getCallbackURI();
		}
		String remoteID = sc.getChannelID().getRemoteID();

		ChannelConfigurationCommand ccc = new ChannelConfigurationCommand(sc.getConfiguration(),
				callbackURI);
		ccc.execute(this);
		logger.info("Channel configured");
	}

	public synchronized void sendTaggedData(int tag, int flags, byte[] data) {
		getSender().enqueue(tag, flags, data, this);
	}

	protected boolean step() throws IOException {
		int avail = inputStream.available();
		if (avail == 0) {
			return false;
		}
		if (state == STATE_IDLE && avail >= HEADER_LEN) {
			readFromStream(inputStream, rhdr, 0);
			tag = unpack(rhdr, 0);
			flags = unpack(rhdr, 4);
			len = unpack(rhdr, 8);
			if (len > 20000) {
				System.out.println("Big len: " + len);
			}
			data = new byte[len];
			dataPointer = 0;
			state = STATE_RECEIVING_DATA;
		}
		if (state == STATE_RECEIVING_DATA) {
			while (avail > 0 && dataPointer < len) {
				dataPointer += inputStream.read(data, dataPointer, Math.min(avail, len
						- dataPointer));
				avail = inputStream.available();
			}
			if (dataPointer == len) {
				state = STATE_IDLE;
				boolean fin = (flags & FINAL_FLAG) != 0;
				boolean error = (flags & ERROR_FLAG) != 0;
				if ((flags & REPLY_FLAG) != 0) {
					// reply
					handleReply(tag, fin, error, len, data);
				}
				else {
					// request
					handleRequest(tag, fin, error, len, data);
				}
			}
		}
		return true;
	}

	public void purge(KarajanChannel channel) throws IOException {
		getSender().purge(this, channel);
	}

	protected void register() {
		getMultiplexer(FAST).register(this);
	}

	private static final int SENDER_COUNT = 1;
	private static Sender[] sender;
	private static int crtSender;

	private static synchronized Sender getSender() {
		if (sender == null) {
			sender = new Sender[SENDER_COUNT];
			for (int i = 0; i < SENDER_COUNT; i++) {
				sender[i] = new Sender();
				sender[i].start();
			}
		}
		try {
			return sender[crtSender++];
		}
		finally {
			if (crtSender == SENDER_COUNT) {
				crtSender = 0;
			}
		}
	}

	private static class SendEntry {
		public final int tag, flags;
		public final byte[] data;
		public final AbstractStreamKarajanChannel channel;

		public SendEntry(int tag, int flags, byte[] data, AbstractStreamKarajanChannel channel) {
			this.tag = tag;
			this.flags = flags;
			this.data = data;
			this.channel = channel;
		}
	}

	private static class Sender extends Thread {
		private final LinkedList queue;
		private final byte[] shdr;

		public Sender() {
			super("Sender");
			queue = new LinkedList();
			setDaemon(true);
			shdr = new byte[HEADER_LEN];
		}

		public synchronized void enqueue(int tag, int flags, byte[] data,
				AbstractStreamKarajanChannel channel) {
			queue.addLast(new SendEntry(tag, flags, data, channel));
			notify();
		}

		public void run() {
			try {
				SendEntry e;
				while (true) {
					synchronized (this) {
						while (queue.isEmpty()) {
							wait();
						}
						e = (SendEntry) queue.removeFirst();
					}
					try {
						send(e.tag, e.flags, e.data, e.channel.getOutputStream());
					}
					catch (IOException ex) {
						logger.info("Channel IOException", ex);
						synchronized (this) {
							queue.addFirst(e);
						}
						e.channel.handleChannelException(ex);
					}
					catch (Exception ex) {
						ex.printStackTrace();
						try {
							e.channel.getChannelContext().getRegisteredCommand(e.tag).errorReceived(
									null, ex);
						}
						catch (Exception exx) {
							logger.warn(exx);
						}
					}
				}
			}
			catch (InterruptedException e) {
				// exit
			}
		}

		public void purge(KarajanChannel source, KarajanChannel channel) throws IOException {
			SendEntry e;
			synchronized (this) {
				Iterator i = queue.iterator();
				while (i.hasNext()) {
					e = (SendEntry) i.next();
					if (e.channel == source) {
						channel.sendTaggedData(e.tag, e.flags, e.data);
						i.remove();
					}
				}
			}
		}

		private void send(int tag, int flags, byte[] data, OutputStream os) throws IOException {
			pack(shdr, 0, tag);
			pack(shdr, 4, flags);
			pack(shdr, 8, data.length);
			synchronized (os) {
				os.write(shdr);
				os.write(data);
				if ((flags & FINAL_FLAG) != 0) {
					os.flush();
				}
			}
		}
	}

	private static final int MUX_COUNT = 2;
	private static Multiplexer[] multiplexer;
	public static final int FAST = 0;
	public static final int SLOW = 1;

	public static synchronized Multiplexer getMultiplexer(int n) {
		if (multiplexer == null) {
			multiplexer = new Multiplexer[MUX_COUNT];
			for (int i = 0; i < MUX_COUNT; i++) {
				multiplexer[i] = new Multiplexer();
				multiplexer[i].start();
			}
		}
		return multiplexer[n];
	}

	protected static class Multiplexer extends Thread {
		private Set channels;
		private List remove, add;

		public Multiplexer() {
			super("Channel multiplexer");
			setDaemon(true);
			channels = new HashSet();
			remove = new ArrayList();
			add = new ArrayList();
		}

		public synchronized void register(AbstractStreamKarajanChannel channel) {
			add.add(channel);
		}

		public void run() {
			boolean any;
			try {
				while (true) {
					any = false;
					Iterator i = channels.iterator();
					while (i.hasNext()) {
						AbstractStreamKarajanChannel channel = (AbstractStreamKarajanChannel) i.next();
						if (channel.isClosed()) {
							i.remove();
						}
						try {
							any |= channel.step();
						}
						catch (Exception e) {
							shutdown(channel, e);
						}
					}
					synchronized (this) {
						i = remove.iterator();
						while (i.hasNext()) {
							channels.remove(i.next());
						}
						i = add.iterator();
						while (i.hasNext()) {
							channels.add(i.next());
						}
						remove.clear();
						add.clear();
					}
					if (!any) {
						Thread.sleep(20);
					}
				}
			}
			catch (Exception e) {
				logger.warn("Exception in channel multiplexer", e);
			}
		}

		private void shutdown(AbstractStreamKarajanChannel channel, Exception e) {
			if (logger.isDebugEnabled()) {
				logger.debug("Channel exception caught", e);
			}
			channel.handleChannelException(e);
			remove.add(channel);
		}
	}
}
