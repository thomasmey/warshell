package de.m3y3r.warshell;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.RemoteEndpoint.Basic;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;

@ServerEndpoint("/ws")
public class WarShell {

	private static final Map<Session, PtyProcess> processes = Collections.synchronizedMap(new HashMap<>());
	private static final Map<Session, Thread> ioThreads = Collections.synchronizedMap(new HashMap<>());

	private static final Logger log = Logger.getLogger(WarShell.class.getCanonicalName());
	private String[] cmd = new String[] {"/bin/sh"};

	@OnOpen
	public void newShell(Session session) throws IOException {
		log.log(Level.INFO, "new connection {0}", session);
		PtyProcessBuilder pb = new PtyProcessBuilder(cmd);
		Map<String, String> env = new HashMap<>();
		env.put("TERM", "xterm");
		pb.setEnvironment(env);
		PtyProcess p = pb.start();
		processes.put(session, p);

		Basic basicRemote = session.getBasicRemote();
		Runnable io = () -> {
			byte[] inBuffer = new byte[1024];
			try(InputStream in = p.getInputStream()) {
				while(true) {
					if(Thread.interrupted()) {
						break;
					}
					try(OutputStream out = basicRemote.getSendStream()) {
						int len = in.read(inBuffer);
						out.write(inBuffer, 0, len);
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		};
		Thread thread = new Thread(io, "io-" + session.getId());
		thread.start();
		ioThreads.put(session, thread);
	}

	@OnMessage
	public void io(Session session, String msg) throws IOException {
		PtyProcess p = processes.get(session);
		OutputStream out = p.getOutputStream();
		out.write(msg.getBytes());
	}

	//TODO: send as binary in javascript
//	@OnMessage
//	public void io(Session session, boolean isLast, byte[] msg) throws IOException {
//		log.log(Level.INFO, "byte message {0}", Arrays.toString(msg));
//		Process p = processes.get(session);
//		OutputStream out = p.getOutputStream();
//		out.write(msg);
//	}

	@OnError
	public void error(Session session, Throwable throwable) {
		log.log(Level.SEVERE, "Error in session " + session, throwable);
	}

	@OnClose
	public void close(Session session, CloseReason closeReason) throws InterruptedException, IOException {
		log.log(Level.INFO, "Closing session {0} - closeReason {1}", new Object[] {session, closeReason});
		PtyProcess p = processes.remove(session);
		if(p != null) {
			p.getInputStream().close();
			p.getOutputStream().close();
			p.destroy();
		}

		Thread t = ioThreads.remove(session);
		if(t != null) {
			t.interrupt();
		}
		while(t.isAlive()) {
			log.log(Level.INFO, "Thread {0} still alive wait a bit to die", t.getName());
			t.join(TimeUnit.SECONDS.toMillis(3));
		}
	}
}
