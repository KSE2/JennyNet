/*
*  File: ReflectClient.java
* 
*  Project JennyNet
*  @author Wolfgang Keller
*  
*  Copyright (c) 2025 by Wolfgang Keller, Munich, Germany
* 
This program is not public domain software but copyright protected to the 
author(s) stated above. However, you can use, redistribute and/or modify it 
under the terms of the The GNU General Public License (GPL) as published by
the Free Software Foundation, version 3.0 of the License.

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the License along with this program; if not,
write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, 
Boston, MA 02111-1307, USA, or go to http://www.gnu.org/copyleft/gpl.html.
*/

package org.kse.jennynet.appl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;

import org.kse.jennynet.core.Client;
import org.kse.jennynet.core.ConnectionMonitor;
import org.kse.jennynet.core.JennyNet;
import org.kse.jennynet.intfa.ComDirection;
import org.kse.jennynet.intfa.ConnectionParameters;
import org.kse.jennynet.intfa.SendPriority;
import org.kse.jennynet.intfa.Connection.ConnectionState;
import org.kse.jennynet.util.EventReporter;
import org.kse.jennynet.util.Util;

/** JennyNet testing tool comprising a client which can connect to a 
 * server and issue actions via commandline.
 */
public class ReflectClient {
	public static final int BUILD_VERSION = 1;

	private Client client;
	private ConnectionParameters params = JennyNet.getConnectionParameters();
	
	public ReflectClient() {
	}

	public static void main (String[] args) {
		System.out.println("REFLECT CLIENT (build " + BUILD_VERSION + ")");
		
		try {
			ReflectClient reflect = new ReflectClient();
			reflect.defineParameters(args);
			reflect.commandLoop();
			System.out.println("REFLECT CLIENT terminated");
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			System.exit(0);
		}
	}

	private void defineParameters(String[] args) throws IOException {
	   File dir = new File("test/reflect/client");
	   dir.mkdirs();
	   params.setFileRootDir(dir);
	}

	private void commandLoop() {
		
		int blockCrc = 0;
		long blockID = 0;
		boolean terminate = false;
		boolean autoMonitor = false;
		boolean unrecognised;
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		
		while (!terminate) {
			try {
				String input = reader.readLine();
				if (input == null) return;
				input = input.trim().replaceAll("  ", " ");
				System.out.println("-- command [" + input + "]");
				String[] words = input.split(" ");
				if (words.length == 0) continue;

				unrecognised = false;
				String cmd  = words[0];
				if (input.equals("exit") | input.equals("quit")) {
					terminate = true;
					shutdownClient();
					unrecognised = true;

				} else if (cmd.equals("client")) {
					if (client != null && client.isConnected()) {
						errAnswer("client currently operative: " + client);
					} else {
						client = new Client();
						client.addListener(new EventReporter());
						client.setParameters(params);
						
						// if available bind to port
						if (words.length > 1) {
							int port = Integer.parseInt(words[1]);
							client.bind(port);
						}
						answer("-- new client " + client.getLocalAddress() + ", unconnected");
					}
					
				} else if (cmd.equals("connect") && words.length > 2) {
					try {
						String domain = words[1];
						int port = Integer.parseInt(words[2]);
						if (client == null || client.isClosed()) {
							client = new Client();
							client.addListener(new EventReporter());
							client.setParameters(params);
						}
						if (client.isConnected()) {
							errAnswer("client is currently connected to " + client.getRemoteAddress());
					    } else  if (client.getOperationState() == ConnectionState.UNCONNECTED) {
							client.connect(10000, domain, port);
							answer("-- client " + client.getLocalAddress().getPort() + " connected to " + client.getRemoteAddress());
						}
					} catch (Exception e) {
						errAnswer("bad or unreachable address: " + words[1] + ", port " + words[2]);
						errAnswer("client state = " + client.getOperationState());
						errAnswer(e.toString());
					}

				} else if (cmd.equals("tempo") && words.length > 1) {
					if (checkConnected())
					try {
						int tempo = Integer.parseInt(words[1]);
						client.setTempo(tempo);
						Util.sleep(500);
						answer("-- new TEMPO is: " + client.getTransmissionSpeed());
					} catch (Exception e) {
						errAnswer(e.toString());
					}
					
				} else if (cmd.equals("close")) {
					if (client != null) {
						if (words.length > 1 && words[1].equals("hard")) {
							client.closeHard();
						} else {
							client.close();
						}
//						client.waitForClosed(120000);
						answer("-- closing initiated: " + client);
					}
					
				} else if (cmd.equals("monitor")) {
					monitor();
					unrecognised = true;
					
				} else if (cmd.equals("ping")) {
					if (checkConnected()) {
						client.sendPing();
						answer("-- ping sent to " + client.getRemoteAddress());
						Util.sleep(500);
						answer("-- PING VALUE = " + client.getLastPingTime());
					}
					
				} else if (cmd.equals("alive") && words.length > 1) {
					if (client != null) {
						int period = Integer.parseInt(words[1]);
						client.getParameters().setAlivePeriod(period);
					}					
					
				} else if (cmd.equals("idle") && words.length > 1) {
					if (client != null) {
						int threshold = Integer.parseInt(words[1]);
						client.getParameters().setIdleThreshold(threshold);
					}					
					
				} else if (cmd.equals("sendfile") && words.length > 2) {
					if (checkConnected())
					try {
						File f = new File(words[1]);
						SendPriority prio = SendPriority.NORMAL;
						if (words.length > 3) {
							prio = SendPriority.valueOf(words[3].toUpperCase());
						}
						if (!f.isFile()) {
							errAnswer("file not found: " + f.getAbsolutePath());
						} else {
							answer("-- sending file: " + f.getAbsolutePath() + ", path = " + words[2]);
							client.sendFile(f, words[2], prio);
						}
					} catch (Exception e) {
						errAnswer("*** Error: " + e);
					}
					
				} else if (cmd.equals("sendtext") && words.length > 1) {
					if (checkConnected())
					try {
						String text = "";
						for (int i = 1; i < words.length; i++) {
							text += words[i] + " ";
						}
						text = text.trim();
						if (text.startsWith("\"") && text.endsWith("\"")) {
							text = text.substring(1, text.length()-1);
						}
						answer("-- sending text-object: " + text);
						client.sendObject(text);
					} catch (Exception e) {
					}
					
				} else if (cmd.equals("sendobject") && words.length > 1) {
					if (checkConnected())
					try {
						int size = Integer.parseInt(words[1]);
						answer("-- sending object datra of length: " + size);
						byte[] data1 = Util.randBytes(size);
						blockCrc = Util.CRC32_of(data1); 
						blockID = client.sendObject(data1);
						answer("--> sending DATA-BLOCK, id = " + blockID);
					} catch (Exception e) {
						e.printStackTrace();
					}
					
				} else if (cmd.equals("automon") && words.length > 1) {
					boolean v = Boolean.parseBoolean(words[1]);
					autoMonitor = v;
					
				} else if (cmd.equals("break") && words.length > 2) {
					if (checkConnected())
					try {
						long id = Long.parseLong(words[2]);
						ComDirection dir = ComDirection.INCOMING;
						if (words[1].equals("in")) {
						} else if (words[1].equals("out")) {
							dir = ComDirection.OUTGOING;
						} else {
							throw new IllegalArgumentException("illegal direction value: " + words[1]);
						}
						client.breakTransfer(id, dir);
					} catch (Exception e) {
						errAnswer("*** Error: " + e);
					}

				} else if (cmd.equals("help")) {
					String text = "   client [<port>]\n   connect <domain> <port>\n   tempo <baud>\n   ping" 
				                  + "\n   alive <period>\n   idle <threshold>\n   monitor\n   automon <true|false>"
							      + "\n   sendobject <size>\n   sendfile <file> <target> [<prio>]\n   sendtext <text>\n   break {in|out} <oid>\n   close [hard]\n   exit";
					answer("List of Commands:");
					answer(text);
					unrecognised = true;
					
				} else {
					errAnswer("** unrecognisable command");
					unrecognised = true;
				}
			
			// auto-monitor execution
			if (autoMonitor & !unrecognised) {
				monitor();
			}
				
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		} // while
		
	}

	private boolean checkConnected () {
		if (client == null) {
			errAnswer("no client defined");
			return false;
		}
		if (!client.isConnected()) {
			errAnswer("client not connected");
			return false;
		}
		return true;
	}
	
	private void shutdownClient () {
		if (client != null && client.isConnected()) {
			client.closeHard();
		}
	}

	private void monitor () {
		if (client != null) {
			InetSocketAddress addr = client.getLocalAddress();
			ConnectionMonitor mo = client.getMonitor();
			answer("CLIENT MONITOR (" + (addr == null ? "" : addr.getPort()) + ")");
			System.out.println(mo.report(5));
		} else {
			errAnswer("no client defined");
		}
	}
	
	private static void errAnswer (String text) {
		System.err.println(text);
	}
	
	private static void answer (String text) {
		System.out.println(text);
	}
	
	
}
