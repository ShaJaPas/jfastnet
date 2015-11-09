/*******************************************************************************
 * Copyright 2015 Klaus Pfeiffer <klaus@allpiper.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.jfastnet;

import com.esotericsoftware.kryo.Kryo;
import com.jfastnet.config.SerialiserConfig;
import com.jfastnet.idprovider.ClientIdReliableModeIdProvider;
import com.jfastnet.idprovider.IIdProvider;
import com.jfastnet.messages.Message;
import com.jfastnet.messages.MessagePart;
import com.jfastnet.peers.javanet.JavaNetPeer;
import com.jfastnet.processors.*;
import com.jfastnet.serialiser.ISerialiser;
import com.jfastnet.serialiser.KryoSerialiser;
import com.jfastnet.time.ITimeProvider;
import com.jfastnet.time.SystemTimeProvider;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
@Setter
@Accessors(chain = true)
public class Config {

	/** Message receiver that will simply call process on the message. */
	public static final IMessageReceiver DEFAULT_MESSAGE_RECEIVER = Message::process;

	/** Are we the host? UdpServer sets this to true on creation. */
	public boolean isHost;

	/** Hostname or IP address. */
	public String host = "127.0.0.1";

	/** Port number to accept or request new UDP connections on. */
	public int port = 0;

	/** On client this can be 0 so a free port is automatically used. */
	public int bindPort = 0;

	/** Optional configured sender id. */
	public int senderId;

	/** Set to true if you want that a CSV file is created after every run with
	 * data about all the sent and received messages and their data size. */
	public boolean trackData;

	/** Collected data. Only used if trackData is set to true. */
	public NetStats netStats = new NetStats();

	/** Used for the timestamp for new messages. */
	public ITimeProvider timeProvider = new SystemTimeProvider();

	/** Provides the message ids. */
	public IIdProvider idProvider = new ClientIdReliableModeIdProvider();

	/** Library internal message sender. */
	public IMessageSender sender;

	/** Library internal message receiver for received messages. */
	public IMessageReceiver receiver;

	/** Configure an external receiver for incoming messages. Must be thread-safe. */
	public IMessageReceiver externalReceiver = DEFAULT_MESSAGE_RECEIVER;

	/** UDP peer system to use. (e.g. KryoNetty) */
	public IPeer udpPeer = new JavaNetPeer(this);

	/** Serialisation system. Some peers require specific serialisation
	 * return types. */
	public ISerialiser serialiser = new KryoSerialiser(new SerialiserConfig(), new Kryo());

	/** Reliable UDP connection established. */
	public boolean connectionEstablished;

	/** Used for receiving bigger messages. Only one byte array buffer may
	 * be processed at any given time. */
	public SortedMap<Integer, SortedMap<Integer, MessagePart>> byteArrayBufferMap = new TreeMap<>();

	/** Required for the reliable sequence mode. Interval in ms. */
	public int keepAliveInterval = 3000;

	/** Time in ms when peer considers other side as not reachable. */
	public int timeoutThreshold = keepAliveInterval * 6; //2;

	// BEGIN server config
	/** All client ids that are expected to join. */
	public List<Integer> expectedClientIds = new ArrayList<>();

	/** Map of clients that are required to connect.
	 * Key: client id, value: true if connected. */
	public Map<Integer, Boolean> requiredClients = new ConcurrentHashMap<>();

	/** Time that has to be passed to consider a received connect request as
	 * new. */
	public int timeSinceLastConnectRequest = 3000;

	/** Called by the server. */
	public IServerHooks serverHooks = new IServerHooks() {};
	// END server config

	// BEGIN client config
	/** Time in ms the client tries to connect to the server. */
	public int connectTimeout = 5000;

	/** Client will only receive messages, if connected. */
	public volatile boolean connected = false;
	// END client config

	public int maximumUdpPacketSize = 1024;

	public int messageQueueThreshold = 37;
	//SocketOption send buffer SO_SNDBUF
	public int socketSendBufferSize = 131072; //65536;
	public int socketReceiveBufferSize = 65536;

	public int receiveBufferAllocator = 65536;

	/** Maximum number of ids to request when not in sync anymore. */
	public int maximumRequestAbsentIds = 5;

	/** Delay in ms between sending of queued messages. */
	public int queuedMessagesDelay = 50;

	/** Set to true if you want to simulate packet loss or an otherwise
	 * rough environment. */
	public boolean debug = false;

	/** Specify percentage of lost packages from 0 to 100 where 100 means
	 * every packet. */
	public int debugLostPackagePercentage = 1;

	/** Message log collects messages for resending. */
	public MessageLog messageLog = new MessageLog();

	/** List of all added processors. */
	public List<Object> processors = new ArrayList<>();

	/** List of systems that need to be processed every tick. */
	public List<ISimpleProcessable> processables = new ArrayList<>();
	public List<IMessageSenderPreProcessor> messageSenderPreProcessors = new ArrayList<>();
	public List<IMessageSenderPostProcessor> messageSenderPostProcessors = new ArrayList<>();
	public List<IMessageReceiverPreProcessor> messageReceiverPreProcessors = new ArrayList<>();
	public List<IMessageReceiverPostProcessor> messageReceiverPostProcessors = new ArrayList<>();

	public Config() {
		// add default processors in the order in which they get called
		addProcessor(new AddChecksumProcessor());
		addProcessor(new DiscardWrongChecksumMessagesHandler());

		addProcessor(new MessageLogProcessor(this));

		addProcessor(new ReliableModeAckProcessor(this));
		addProcessor(new ReliableModeSequenceProcessor(this));
		//addProcessor(new OrderedUdpHandler(this));
		addProcessor(new DiscardMessagesHandler());
	}

	/**
	 * Copy constructor. Only primitve values are copied to prevent problems
	 * with references to already used object instances.
	 * @param copy config object to copy
	 */
	public Config(Config copy) {
		this();
		this.host = copy.host;
		this.port = copy.port;
		this.bindPort = copy.bindPort;
		this.senderId = copy.senderId;
		this.trackData = copy.trackData;
		this.maximumUdpPacketSize = copy.maximumUdpPacketSize;
		this.messageQueueThreshold = copy.messageQueueThreshold;
	}

	public void addProcessor(Object processor) {
		processors.add(processor);
		if (processor instanceof ISimpleProcessable) {
			ISimpleProcessable processable = (ISimpleProcessable) processor;
			processables.add(processable);
		}
		if (processor instanceof IMessageSenderPreProcessor) {
			IMessageSenderPreProcessor messageSenderPreProcessor = (IMessageSenderPreProcessor) processor;
			messageSenderPreProcessors.add(messageSenderPreProcessor);
		}
		if (processor instanceof IMessageSenderPostProcessor) {
			IMessageSenderPostProcessor messageSenderPostProcessor = (IMessageSenderPostProcessor) processor;
			messageSenderPostProcessors.add(messageSenderPostProcessor);
		}
		if (processor instanceof IMessageReceiverPreProcessor) {
			IMessageReceiverPreProcessor messageReceiverPreProcessor = (IMessageReceiverPreProcessor) processor;
			messageReceiverPreProcessors.add(messageReceiverPreProcessor);
		}
		if (processor instanceof IMessageReceiverPostProcessor) {
			IMessageReceiverPostProcessor messageReceiverPostProcessor = (IMessageReceiverPostProcessor) processor;
			messageReceiverPostProcessors.add(messageReceiverPostProcessor);
		}
	}

	public <E> E getProcessorOf(Class<E> clazz) {
		for (Object processor : processors) {
			if (clazz.isAssignableFrom(processor.getClass())) {
				return (E) processor;
			}
		}
		return null;
	}
}