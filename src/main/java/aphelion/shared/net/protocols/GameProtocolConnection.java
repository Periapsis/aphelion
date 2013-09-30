/*
 * Aphelion
 * Copyright (c) 2013  Joris van der Wel
 * 
 * This file is part of Aphelion
 * 
 * Aphelion is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 * 
 * Aphelion is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with Aphelion.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * In addition, the following supplemental terms apply, based on section 7 of
 * the GNU Affero General Public License (version 3):
 * a) Preservation of all legal notices and author attributions
 * b) Prohibition of misrepresentation of the origin of this material, and
 * modified versions are required to be marked in reasonable ways as
 * different from the original version (for example by appending a copyright notice).
 * 
 * Linking this library statically or dynamically with other modules is making a
 * combined work based on this library. Thus, the terms and conditions of the
 * GNU Affero General Public License cover the whole combination.
 * 
 * As a special exception, the copyright holders of this library give you 
 * permission to link this library with independent modules to produce an 
 * executable, regardless of the license terms of these independent modules,
 * and to copy and distribute the resulting executable under terms of your 
 * choice, provided that you also meet, for each linked independent module,
 * the terms and conditions of the license of that module. An independent
 * module is a module which is not derived from or based on this library.
 */

package aphelion.shared.net.protocols;

import aphelion.shared.event.Workable;
import aphelion.shared.event.WorkerTask;
import aphelion.shared.event.WorkerTaskCallback;
import aphelion.shared.net.PROTOCOL;
import aphelion.shared.net.SessionToken;
import aphelion.shared.net.WS_CLOSE_STATUS;
import aphelion.shared.net.WebSocketTransport;
import aphelion.shared.net.protobuf.GameC2S;
import aphelion.shared.net.protobuf.GameC2S.TimeRequest;
import aphelion.shared.net.protobuf.GameS2C;
import aphelion.shared.swissarmyknife.Attachable;
import aphelion.shared.swissarmyknife.AttachmentData;
import aphelion.shared.swissarmyknife.AttachmentManager;
import aphelion.shared.swissarmyknife.ThreadSafe;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a game protocol connection to the client or server. 
 * Multiple sockets may be used to send the data.
 * @author Joris
 */
public class GameProtocolConnection implements Attachable
{
        private static final Logger log = Logger.getLogger("aphelion.net");
        
        public static final AttachmentManager attachmentManager = new AttachmentManager();
        private AttachmentData attachment = attachmentManager.getNewDataContainer();
        
        private final Workable workable;
        private final WebSocketTransport websocketTransport;
        public  final SessionToken session;
        public  final boolean server;
        private final GameListener listener;
        
        private final STATE[] state = {STATE.NONE};
        
        private static enum STATE {NONE, CREATED, DROPPED};

        public GameProtocolConnection(Workable workable, WebSocketTransport websocketTransport, SessionToken session, boolean server, GameListener listener)
        {
                this.workable = workable;
                this.websocketTransport = websocketTransport;
                this.session = session;
                this.server = server;
                this.listener = listener;
        }
        
        @Override
        public AttachmentData getAttachments()
        {
                return attachment;
        }
        
        @ThreadSafe
        public void send(GameS2C.S2COrBuilder s2c, WorkerTaskCallback<Object> callback)
        {
                if (!server)
                {
                        throw new IllegalStateException();
                }
                
                // callback is fired when the message has been sent to the socket
                workable.addWorkerTask(new EncodeS2CWork(), s2c, callback);
        }
        
        @ThreadSafe
        public void send(GameC2S.C2SOrBuilder c2s, WorkerTaskCallback<Object> callback)
        {
                if (server)
                {
                        throw new IllegalStateException();
                }
                
                workable.addWorkerTask(new EncodeC2SWork(), c2s, callback);
        }
        
        @ThreadSafe
        public void send(GameS2C.S2COrBuilder s2c)
        {
                send(s2c, null);
        }
        
        @ThreadSafe
        public void send(GameC2S.C2SOrBuilder c2s)
        {
                send(c2s, null);
        }
        
        public void requestClose(WS_CLOSE_STATUS code, String message)
        {
                websocketTransport.closeSession(session, PROTOCOL.GAME, code, message);
        }
        
        public void requestClose(WS_CLOSE_STATUS code)
        {
                requestClose(code, "");
        }
        
        @ThreadSafe
        public void created()
        {
                synchronized(state)
                {
                        if (state[0] == STATE.NONE)
                        {
                                state[0] = STATE.CREATED;
                                workable.runOnMain(new CallGameClientListener(listener, this, 1, null, null));
                        }
                        else
                        {
                                log.log(Level.WARNING, "created() in an incorrect state {0}", state[0]);
                        }
                }
        }
        
        @ThreadSafe
        public void removed()
        {
                synchronized(state)
                {
                        if (state[0] == STATE.CREATED)
                        {
                                state[0] = STATE.DROPPED;
                                workable.runOnMain(new CallGameClientListener(listener, this, 2, null, null));
                        }
                        else
                        {
                                log.log(Level.WARNING, "removed() in an incorrect state {0}", state[0]);
                        }
                }
        }
        
        /** Immediately parse an incoming message. This is an expensive call
         * @param message The nano time at which this message was received
         * @param receivedAt The nano time at which this message was received 
         */
        @ThreadSafe
        public void parseMessage(ByteBuffer message, long receivedAt)
        {
                synchronized(state)
                {
                        if (state[0] != STATE.CREATED)
                        {
                                log.log(Level.WARNING, "parseMessage() in an incorrect state {0}", state[0]);
                                return;
                        }
                }
                
                if (server)
                {
                        try
                        {
                                // this method is called from one of the websocket worker threads
                                GameC2S.C2S c2s = GameC2S.C2S.parseFrom(new ByteArrayInputStream(message.array(), message.position(), message.remaining()));

                                // parse these immediately as a shortcut to get a better time measurement
                                // While possible to optimize further, this is not needed. 
                                // With client and server on the same machine (my machine) requests are 
                                // sent and received within 1 millisecond.
                                for (TimeRequest timeRequest : c2s.getTimeRequestList())
                                {
                                        GameS2C.S2C.Builder s2c = GameS2C.S2C.newBuilder();
                                        GameS2C.TimeResponse.Builder response = s2c.addTimeResponseBuilder();
                                        response.setClientTime(timeRequest.getClientTime());
                                        response.setServerTime(System.nanoTime());
                                        
                                        EncodeS2CWork work = new EncodeS2CWork();
                                        try
                                        {
                                                work.work(s2c);
                                        }
                                        catch (WorkerTask.WorkerException ex)
                                        {
                                                log.log(Level.SEVERE, "Unexpected exception", ex);
                                        }
                                }
                                
                                // run the callback on the main thread
                                workable.runOnMain(new CallGameClientListener(listener, this, c2s, receivedAt));
                        }
                        catch (InvalidProtocolBufferException ex)
                        {
                                log.log(Level.SEVERE, "Protobuf Exception while parsing a message as a server", ex);
                        }
                        catch (IOException ex)
                        {
                                log.log(Level.SEVERE, "IOException while parsing a message as a server", ex);
                        }
                        
                }
                else
                {
                        try
                        {
                                // this method is called from one of the websocket worker threads
                                GameS2C.S2C s2c = GameS2C.S2C.parseFrom(new ByteArrayInputStream(message.array(), message.position(), message.remaining()));

                                // run the callback on the main thread
                                workable.runOnMain(new CallGameClientListener(listener, this, s2c, receivedAt));
                        }
                        catch (InvalidProtocolBufferException ex)
                        {
                                log.log(Level.SEVERE, "Protobuf Exception while parsing a message as a client", ex);
                        }
                        catch (IOException ex)
                        {
                                log.log(Level.SEVERE, "IOException while parsing a message as a client", ex);
                        }
                }
        }
        
        @ThreadSafe
        public void connectionAdded()
        {
                workable.runOnMain(new CallGameClientListener(listener, this, 4, null, null));
        }
        
        @ThreadSafe
        public void connectionDropped(WS_CLOSE_STATUS drop_code, String drop_reason)
        {
                workable.runOnMain(new CallGameClientListener(listener, this, 5, drop_code, drop_reason));
        }
        
        
        // a cache that kicks in when we have to send the same message multiple times.
        private static Map<GameS2C.S2COrBuilder, byte[]> s2cCache = Collections.synchronizedMap(new WeakHashMap<GameS2C.S2COrBuilder, byte[]>());
        private static Map<GameC2S.C2SOrBuilder, byte[]> c2sCache = Collections.synchronizedMap(new WeakHashMap<GameC2S.C2SOrBuilder, byte[]>());
        
        private class EncodeS2CWork extends WorkerTask<GameS2C.S2COrBuilder, Object>
        {
                
                @Override
                public Object work(GameS2C.S2COrBuilder s2cOrBuilder) throws WorkerTask.WorkerException
                {
                        byte[] result;
                        
                        result = s2cCache.get(s2cOrBuilder);
                        
                        if (result != null)
                        {
                                result = result.clone();
                                // Need to clone because websocketTransport needs to reserve a few bytes
                        }
                        else
                        {
                                GameS2C.S2C s2c;
                                if (s2cOrBuilder instanceof GameS2C.S2C.Builder)
                                {
                                        synchronized(s2cOrBuilder)
                                        {
                                                // protobuf does not support concurrent calls to build()
                                                // since it modifies internal state (even though it returns 
                                                // a new instance of GameS2C.S2C, which is immutable by API, 
                                                // the old object is also no longer mutable by use of Error 
                                                // exceptions)
                                                s2c = ((GameS2C.S2C.Builder) s2cOrBuilder).build();
                                        }
                                }
                                else
                                {
                                        s2c = (GameS2C.S2C) s2cOrBuilder;
                                }

                                int size = s2c.getSerializedSize();
                                result = new byte[size + WebSocketTransport.SEND_RESERVEDPREFIX_BYTES];
                                
                                CodedOutputStream output = CodedOutputStream.newInstance(result, WebSocketTransport.SEND_RESERVEDPREFIX_BYTES, size);

                                try
                                {
                                        s2c.writeTo(output);
                                }
                                catch (IOException ex)
                                {
                                        throw new WorkerTask.WorkerException(ex);
                                }
                                
                                // assert that there are no bytes left
                                output.checkNoSpaceLeft();
                                
                                s2cCache.put(s2cOrBuilder, result);
                        }
                        
                        try
                        {
                                // the websocket library will encode the bytes into websocket frames from this worker thread
                                websocketTransport.send(session, PROTOCOL.GAME, result);
                        }
                        catch (WebSocketTransport.NoSuitableConnection ex)
                        {
                                throw new WorkerTask.WorkerException(ex);
                        }

                        return null; // callback return argument is not used
                }
        }
        
        private class EncodeC2SWork extends WorkerTask<GameC2S.C2SOrBuilder, Object>
        {
                @Override
                public Object work(GameC2S.C2SOrBuilder c2sOrBuilder) throws WorkerTask.WorkerException
                {
                        byte[] result;
                        
                        result = c2sCache.get(c2sOrBuilder);
                        
                        if (result != null)
                        {
                                result = result.clone();
                                // Need to clone because websocketTransport needs to reserve a few bytes
                        }
                        else
                        {
                                GameC2S.C2S c2s;
                                if (c2sOrBuilder instanceof GameC2S.C2S.Builder)
                                {
                                        synchronized (c2sOrBuilder)
                                        {
                                                try
                                                {
                                                        // special case for a small amount of extra accuracy
                                                        if (c2sOrBuilder.getTimeRequestCount() > 0)
                                                        {
                                                                for (TimeRequest.Builder timeRequest : ((GameC2S.C2S.Builder) c2sOrBuilder).getTimeRequestBuilderList())
                                                                {
                                                                        timeRequest.setClientTime(System.nanoTime());
                                                                }
                                                        }
                                                }
                                                catch (Error ex)
                                                {
                                                        // no longer mutable, leave the time stamp alone
                                                }

                                                c2s = ((GameC2S.C2S.Builder) c2sOrBuilder).build();
                                        }
                                }
                                else
                                {
                                        c2s = (GameC2S.C2S) c2sOrBuilder;
                                }

                                int size = c2s.getSerializedSize();
                                result = new byte[size + WebSocketTransport.SEND_RESERVEDPREFIX_BYTES];
                                
                                CodedOutputStream output = CodedOutputStream.newInstance(result, WebSocketTransport.SEND_RESERVEDPREFIX_BYTES, size);

                                try
                                {
                                        c2s.writeTo(output);
                                }
                                catch (IOException ex)
                                {
                                        throw new WorkerTask.WorkerException(ex);
                                }
                                
                                // assert that there are no bytes left
                                output.checkNoSpaceLeft();
                                
                                c2sCache.put(c2sOrBuilder, result);
                        }
                        
                        try
                        {
                                // the websocket library will encode the bytes into websocket frames from this worker thread
                                websocketTransport.send(session, PROTOCOL.GAME, result);
                        }
                        catch (WebSocketTransport.NoSuitableConnection ex)
                        {
                                throw new WorkerTask.WorkerException(ex);
                        }

                        return null; // callback return argument is not used
                }
        }
        
        public static class CallGameClientListener implements Runnable
        {
                final GameListener listener;
                final GameC2S.C2S c2s;
                final GameS2C.S2C s2c;
                final GameProtocolConnection conn;
                final int what;
                final long receivedAt;
                final WS_CLOSE_STATUS drop_code;
                final String drop_reason;

                public CallGameClientListener(GameListener listener, GameProtocolConnection conn, int what, WS_CLOSE_STATUS drop_code, String drop_reason)
                {
                        this.listener = listener;
                        this.conn = conn;
                        this.c2s = null;
                        this.s2c = null;
                        this.what = what;
                        this.receivedAt = 0;
                        this.drop_code = drop_code;
                        this.drop_reason = drop_reason;
                }

                // message
                public CallGameClientListener(GameListener listener, GameProtocolConnection conn, GameC2S.C2S c2s, long receivedAt)
                {
                        this.listener = listener;
                        this.conn = conn;
                        this.c2s = c2s;
                        this.s2c = null;
                        this.what = 3;
                        this.receivedAt = receivedAt;
                        this.drop_code = null;
                        this.drop_reason = null;
                }
                
                // message
                public CallGameClientListener(GameListener listener, GameProtocolConnection conn, GameS2C.S2C s2c, long receivedAt)
                {
                        this.listener = listener;
                        this.conn = conn;
                        this.c2s = null;
                        this.s2c = s2c;
                        this.what = 3;
                        this.receivedAt = receivedAt;
                        this.drop_code = null;
                        this.drop_reason = null;
                }

                @Override
                public void run()
                {
                        switch (what)
                        {
                                case 0:
                                        listener.gameEstablishFailure(drop_code, drop_reason);
                                        return;
                                case 1:
                                        listener.gameNewClient(conn);
                                        return;
                                case 2:
                                        listener.gameRemovedClient(conn);
                                        return;
                                case 3:
                                        if (this.c2s != null)
                                        {
                                                listener.gameC2SMessage(conn, c2s, receivedAt);
                                        }
                                        else
                                        {
                                                assert this.s2c != null;
                                                listener.gameS2CMessage(conn, s2c, receivedAt);
                                        }
                                        
                                        return;
                                case 4:
                                        listener.gameNewConnection(conn);
                                        return;
                                case 5:
                                        listener.gameDropConnection(conn, drop_code, drop_reason);
                                        return;
                                default:
                                        assert false;
                        }
                }
        }
}
