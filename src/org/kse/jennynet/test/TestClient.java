/*  File: TestClient.java
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

package org.kse.jennynet.test;

import java.io.File;
import java.io.IOException;

import org.kse.jennynet.core.Client;
import org.kse.jennynet.core.JennyNet;
import org.kse.jennynet.core.JennyNetByteBuffer;
import org.kse.jennynet.util.EventReporter;
import org.kse.jennynet.util.Util;

public class TestClient {

   
   public static void main(String[] args) {
      File tFile;
      
      if (args.length != 2) {
          System.err.println("Usage: <program> <host address> <port number>");
          System.exit(1);
      }
      
      String hostname = args[0];
      int portNumber = Integer.parseInt(args[1]);
      System.out.println("connecting to host = " + hostname + ", port " + portNumber);

      File rootDir = new File("test");
      try {
         JennyNet.getDefaultParameters().setFileRootDir(rootDir);
      } catch (Exception e1) {
         System.out.println("*** cannot activate file-root-directory: " + rootDir.getAbsolutePath());
         System.out.println("    reason: ".concat(e1.toString()));
      }

      Client client = new Client();
      client.addListener(new EventReporter());
//      client.bind(2020);

      try {
         client.getParameters().setConfirmTimeout(5000);
         client.getParameters().setTransmissionParcelSize(64000);
         client.connect(10000, hostname, portNumber);
         
         System.out.println("----- Connection Established ------  to Server Address: " + client.getRemoteAddress() );
         
         // send OBJECT on regular channel
         client.sendObject("Hello, give my friends my greetings!");

//         for (int i = 0; i < 50; i++) {
//            client.sendObject("Hello my Friend, I'm here!");
//         }

         // send test file 2
         tFile = new File("/home/nikola/Downloads/fhl-0-2-2.jar");
         client.sendObject("Hello, we are sending now a file of length = " + tFile.length());
         long fid2 = client.sendFile(tFile, "transmission/fhl-0-2-2.jar");

         Thread.sleep(1500);
         
         // send large object
         JennyNetByteBuffer buf = new JennyNetByteBuffer(Util.randBytes(60000));
         client.sendObject("Hello, we are sending now a data aray with CRC = " + buf.getCRC());
         client.sendObject(buf);
         System.out.println("sent data block with CRC = " + buf.getCRC()) ;

         Thread.sleep(50);
         client.sendPing();
         
//         tFile = new File("/home/wolfgang/FL-Git.txt");
//         tFile = new File("/home/wolfgang/Downloads/jre-6u39-linux-i586.bin");
//         long fid1 = client.sendFile(tFile, "transmission/jre-6.bin");

//         tFile = new File("/home/wolfgang/Downloads/pascal-htmls.tar.gz");
//         long fid2 = client.sendFile(tFile, "transmission/pascal-docs.tar.gz");
//         
         Thread.sleep(2000);
//         client.breakOutgoingTransfer(fid1);
//         Thread.sleep(500);
         client.sendPing();
//         client.sendObject(new JennyNetByteBuffer(null));
         
//         client.waitForSendPerformed();
//         client.close();
         
      } catch (Exception e) {
         e.printStackTrace();
         client.close();
      }
        
   }
}
