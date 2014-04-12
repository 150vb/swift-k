//----------------------------------------------------------------------
//This code is developed as part of the Java CoG Kit project
//The terms of the license can be found at http://www.cogkit.org/license
//This message may not be removed or altered.
//----------------------------------------------------------------------

/*
 * Created on Sep 6, 2005
 */
package org.globus.cog.coaster.channels;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import org.apache.log4j.Logger;
import org.globus.cog.coaster.FallbackAuthorization;
import org.globus.cog.coaster.GSSService;
import org.globus.cog.coaster.RequestManager;
import org.globus.cog.coaster.UserContext;
import org.globus.cog.coaster.commands.ShutdownCommand;
import org.globus.gsi.GSIConstants;
import org.globus.gsi.gssapi.GSSConstants;
import org.globus.gsi.gssapi.GlobusGSSManagerImpl;
import org.globus.gsi.gssapi.auth.Authorization;
import org.globus.gsi.gssapi.auth.HostAuthorization;
import org.globus.gsi.gssapi.auth.SelfAuthorization;
import org.globus.gsi.gssapi.net.GssSocket;
import org.globus.gsi.gssapi.net.GssSocketFactory;
import org.gridforum.jgss.ExtendedGSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;

public class GSSChannel extends AbstractTCPChannel {
	private static final Logger logger = Logger.getLogger(GSSChannel.class);

	private static final boolean streamCompression;
	
	static {
	    streamCompression = "true".equals(System.getProperty("gss.channel.compression.enabled"));
	}

	private GssSocket socket;
	private String peerId;
	private boolean shuttingDown;
	private Exception startException;
	private int id;
	private static int sid = 1;

	public GSSChannel(GssSocket socket, RequestManager requestManager, ChannelContext sc)
			throws IOException {
		super(requestManager, sc, false);
		setSocket(socket);
		this.socket = socket;
		init();
	}

	public GSSChannel(URI contact, RequestManager requestManager, ChannelContext sc) {
		super(requestManager, sc, true);
		setContact(contact);
		init();
	}

	private void init() {
		id = sid++;
	}

	public void start() throws ChannelException {
		reconnect();
		super.start();
	}

	protected void reconnect() throws ChannelException {
		try {
			if (getContact() != null) {
				HostAuthorization hostAuthz = new HostAuthorization("host");

				Authorization authz = new FallbackAuthorization(new Authorization[] { hostAuthz,
						SelfAuthorization.getInstance() });

				GSSCredential cred = this.getChannelContext().getUserContext().getCredential();
				if (cred == null) {
					cred = GSSService.initializeCredentials(true, null, null);
				}

				GSSManager manager = new GlobusGSSManagerImpl();
				ExtendedGSSContext gssContext = (ExtendedGSSContext) manager.createContext(null,
						GSSConstants.MECH_OID, cred, cred.getRemainingLifetime());

				gssContext.requestAnonymity(false);
				gssContext.requestCredDeleg(false);
				//gssContext.requestConf(false);
				gssContext.setOption(GSSConstants.GSS_MODE, GSIConstants.MODE_SSL);
				gssContext.setOption(GSSConstants.DELEGATION_TYPE,
						GSIConstants.DELEGATION_TYPE_LIMITED);
				URI contact = getContact();
				socket = (GssSocket) GssSocketFactory.getDefault().createSocket(contact.getHost(),
						contact.getPort(), gssContext);

				socket.setKeepAlive(true);
				socket.setSoTimeout(0);
				socket.setWrapMode(GSIConstants.MODE_SSL.intValue());
				socket.setAuthorization(authz);
				setSocket(socket);

				logger.info("Connected to " + contact);

				getChannelContext().setRemoteContact(contact.toString());
			}
		}
		catch (Exception e) {
			throw new ChannelException("Failed to start channel " + this, e);
		}
	}

	protected void initializeConnection() {
		try {
			if (socket.getContext().isEstablished()) {
				UserContext uc = getChannelContext().newUserContext(socket.getContext().getSrcName().toString());
				// TODO Credentials should be associated with each
				// individual instance

				// X509Certificate[] chain = (X509Certificate[])
				// ((ExtendedGSSContext)
				// socket.getContext()).inquireByOid(GSSConstants.X509_CERT_CHAIN);
				if (socket.getContext().getCredDelegState()) {
					uc.setCredential(socket.getContext().getDelegCred());
				}
				else {
					uc.setCredential(GSSService.initializeCredentials(true, null, null));
				}
				peerId = uc.getName();
				logger.debug(getContact() + "Peer identity: " + peerId);
			}
			else {
				throw new IOException("Context not established");
			}
		}
		catch (Exception e) {
			logger.warn(getContact() + "Could not get client identity", e);
		}
	}

	public void shutdown() {
		synchronized (this) {
			if (isClosed()) {
				return;
			}
			if (!isLocalShutdown() && isClient()) {
				try {
					ShutdownCommand sc = new ShutdownCommand();
					logger.debug(getContact() + "Initiating remote shutdown");
					sc.execute(this);
					logger.debug(getContact() + "Remote shutdown ok");
				}
				catch (Exception e) {
					logger.warn(getContact() + "Failed to shut down channel nicely", e);
				}
				super.shutdown();
				close();
			}
		}
	}

	@Override
    protected void setInputStream(InputStream inputStream) {
	    if (streamCompression) {
	        super.setInputStream(new InflaterInputStream(inputStream));
	    }
	    else {
	        super.setInputStream(inputStream);
	    }
    }

    @Override
    protected void setOutputStream(OutputStream outputStream) {
        if (streamCompression) {
            super.setOutputStream(new DeflaterOutputStream(outputStream, true));
        }
        else {
            super.setOutputStream(outputStream);
        }
    }

    protected void register() {
		getMultiplexer(SLOW).register(this);
	}

	public String getPeerId() {
		return peerId;
	}

	public String toString() {
		return "GSSChannel [type: " + (isClient() ? "client" : "service") + ", contact: " + getContact() + ", id: " + id + ", context: " + this.getChannelContext() + "]";
	}

	protected synchronized void ensureCallbackServiceStarted() throws Exception {
		if (getCallbackService() == null) {
			setCallbackService(new GSSService(GSSService.initializeCredentials(true, null, null)));
		}
		logger.info("Started local service: " + getCallbackService().getContact());
	}
}
