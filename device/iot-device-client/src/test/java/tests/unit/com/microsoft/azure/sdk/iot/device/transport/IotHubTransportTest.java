// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for full license information.

package tests.unit.com.microsoft.azure.sdk.iot.device.transport;

import com.microsoft.azure.sdk.iot.device.*;
import com.microsoft.azure.sdk.iot.device.exceptions.DeviceClientException;
import com.microsoft.azure.sdk.iot.device.exceptions.IotHubServiceException;
import com.microsoft.azure.sdk.iot.device.exceptions.TransportException;
import com.microsoft.azure.sdk.iot.device.exceptions.UnauthorizedException;
import com.microsoft.azure.sdk.iot.device.transport.*;
import com.microsoft.azure.sdk.iot.device.transport.amqps.AmqpsIotHubConnection;
import com.microsoft.azure.sdk.iot.device.transport.amqps.exceptions.AmqpUnauthorizedAccessException;
import com.microsoft.azure.sdk.iot.device.transport.https.HttpsIotHubConnection;
import com.microsoft.azure.sdk.iot.device.transport.mqtt.MqttIotHubConnection;
import com.microsoft.azure.sdk.iot.device.transport.mqtt.exceptions.MqttUnauthorizedException;
import mockit.*;
import org.junit.Test;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.microsoft.azure.sdk.iot.device.IotHubConnectionStatusChangeReason.*;
import static com.microsoft.azure.sdk.iot.device.transport.IotHubConnectionStatus.*;
import static junit.framework.TestCase.*;

/**
 * Unit tests for IotHubTransportPacket.
 */
public class IotHubTransportTest
{
    @Mocked
    DeviceClientConfig mockedConfig;

    @Mocked
    IotHubStatusCode mockedStatus;

    @Mocked
    Message mockedMessage;

    @Mocked
    IotHubTransportMessage mockedTransportMessage;

    @Mocked
    TransportException mockedTransportException;

    @Mocked
    IotHubTransportPacket mockedPacket;

    @Mocked
    IotHubEventCallback mockedEventCallback;

    @Mocked
    CustomLogger mockedLogger;

    @Mocked
    ScheduledExecutorService mockedScheduledExecutorService;

    @Mocked
    IotHubTransportConnection mockedIotHubTransportConnection;

    @Mocked
    HttpsIotHubConnection mockedHttpsIotHubConnection;

    @Mocked
    AmqpsIotHubConnection mockedAmqpsIotHubConnection;

    @Mocked
    MqttIotHubConnection mockedMqttIotHubConnection;

    @Mocked
    IotHubConnectionStateCallback mockedIotHubConnectionStateCallback;

    @Mocked
    IotHubConnectionStatusChangeCallback mockedIotHubConnectionStatusChangeCallback;

    @Mocked
    IotHubConnectionStatusChangeReason mockedIotHubConnectionStatusChangeReason;

    @Mocked
    RetryPolicy mockedRetryPolicy;

    @Mocked
    RetryDecision mockedRetryDecision;

    @Mocked
    MessageCallback mockedMessageCallback;

    @Mocked
    ScheduledExecutorService mockedTaskScheduler;

    @Mocked
    IotHubTransport.MessageRetryRunnable mockedMessageRetryRunnable;

    @Mocked
    IotHubServiceException mockedIothubServiceException;

    //Tests_SRS_IOTHUBTRANSPORT_34_001: [The constructor shall save the default config.]
    //Tests_SRS_IOTHUBTRANSPORT_34_003: [The constructor shall set the connection status as DISCONNECTED and the current retry attempt to 0.]
    @Test
    public void constructorSucceeds()
    {
        //act
        IotHubTransport transport = new IotHubTransport(mockedConfig);

        //assert
        assertEquals(mockedConfig, Deencapsulation.getField(transport, "defaultConfig"));
        assertEquals(DISCONNECTED, Deencapsulation.getField(transport, "connectionStatus"));
        assertEquals(0, Deencapsulation.getField(transport, "currentReconnectionAttempt"));
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_002: [If the provided config is null, this function shall throw an IllegalArgumentException.]
    @Test (expected = IllegalArgumentException.class)
    public void constructorThrowsForNullConfig()
    {
        //act
        IotHubTransport transport = new IotHubTransport(null);
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_004: [This function shall retrieve a packet from the inProgressPackets queue with the message id from the provided message if there is one.]
    //Tests_SRS_IOTHUBTRANSPORT_34_006: [If there was a packet in the inProgressPackets queue tied to the provided message, and the provided throwable is a TransportException, this function shall call "handleMessageException" with the provided packet and transport exception.]
    @Test
    public void onMessageSentRetrievesFromInProgressAndCallsHandleMessageExceptionForTransportException()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        final String messageId = "1234";
        final Map<String, IotHubTransportPacket> inProgressPackets = new ConcurrentHashMap<>();
        inProgressPackets.put(messageId, mockedPacket);
        Deencapsulation.setField(transport, "connectionStatus", CONNECTED);
        Deencapsulation.setField(transport, "inProgressPackets", inProgressPackets);
        new Expectations(IotHubTransport.class)
        {
            {
                mockedMessage.getMessageId();
                result = messageId;

                Deencapsulation.invoke(transport, "handleMessageException", new Class[] {IotHubTransportPacket.class, TransportException.class}, mockedPacket, mockedTransportException);
            }
        };

        //act
        transport.onMessageSent(mockedMessage, mockedTransportException);

        //assert
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "handleMessageException", new Class[] {IotHubTransportPacket.class, TransportException.class}, mockedPacket, mockedTransportException);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_005: [If there was a packet in the inProgressPackets queue tied to the provided message, and the provided throwable is null, this function shall set the status of that packet to OK_EMPTY and add it to the callbacks queue.]
    @Test
    public void onMessageSentRetrievesFromInProgressAndAddsToCallbackForNoException()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        final String messageId = "1234";
        final Map<String, IotHubTransportPacket> inProgressPackets = new ConcurrentHashMap<>();
        inProgressPackets.put(messageId, mockedPacket);
        Deencapsulation.setField(transport, "connectionStatus", CONNECTED);
        Deencapsulation.setField(transport, "inProgressPackets", inProgressPackets);
        new NonStrictExpectations()
        {
            {
                mockedMessage.getMessageId();
                result = messageId;
            }
        };

        //act
        transport.onMessageSent(mockedMessage, null);

        //assert
        Queue<IotHubTransportPacket> callbackPacketsQueue = Deencapsulation.getField(transport, "callbackPacketsQueue");
        assertEquals(1, callbackPacketsQueue.size());
        assertTrue(callbackPacketsQueue.contains(mockedPacket));
        new Verifications()
        {
            {
                mockedPacket.setStatus(IotHubStatusCode.OK_EMPTY);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_007: [If there was a packet in the inProgressPackets queue tied to the provided message, and the provided throwable is not a TransportException, this function shall call "handleMessageException" with the provided packet and a new transport exception with the provided exception as the inner exception.]
    @Test
    public void onMessageSentRetrievesFromInProgressAndCallsHandleMessageExceptionForNonTransportException()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        final String messageId = "1234";
        final IOException nonTransportException = new IOException();
        final Map<String, IotHubTransportPacket> inProgressPackets = new ConcurrentHashMap<>();
        inProgressPackets.put(messageId, mockedPacket);
        Deencapsulation.setField(transport, "connectionStatus", CONNECTED);
        Deencapsulation.setField(transport, "inProgressPackets", inProgressPackets);
        new Expectations(IotHubTransport.class)
        {
            {
                mockedMessage.getMessageId();
                result = messageId;

                new TransportException(nonTransportException);
                result = mockedTransportException;

                Deencapsulation.invoke(transport, "handleMessageException", new Class[] {IotHubTransportPacket.class, TransportException.class}, mockedPacket, mockedTransportException);
            }
        };

        //act
        transport.onMessageSent(mockedMessage, nonTransportException);

        //assert
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "handleMessageException", new Class[] {IotHubTransportPacket.class, TransportException.class}, mockedPacket, mockedTransportException);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_008: [If this function is called with a non-null message and a non-null throwable, this function shall log an IllegalArgumentException.]
    @Test
    public void onMessageReceivedWithMessageAndExceptionOnlyLogsException()
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);

        //act
        transport.onMessageReceived(mockedTransportMessage, mockedTransportException);

        //assert
        Queue<IotHubTransportPacket> receivedMessagesQueue = Deencapsulation.getField(transport, "receivedMessagesQueue");
        assertTrue(receivedMessagesQueue.isEmpty());
        new Verifications()
        {
            {
                mockedLogger.LogError((IllegalArgumentException) any);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_009: [If this function is called with a non-null message and a null exception, this function shall add that message to the receivedMessagesQueue.]
    @Test
    public void onMessageReceivedWithMessageAndNoExceptionAddsToQueue()
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);

        //act
        transport.onMessageReceived(mockedTransportMessage, null);

        //assert
        Queue<IotHubTransportPacket> receivedMessagesQueue = Deencapsulation.getField(transport, "receivedMessagesQueue");
        assertEquals(1, receivedMessagesQueue.size());
        assertEquals(mockedTransportMessage, receivedMessagesQueue.poll());
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_010: [If this function is called with a null message and a non-null throwable, this function shall log that exception.]
    @Test
    public void onMessageReceivedWithOnlyExceptionOnlyLogsException()
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);

        //act
        transport.onMessageReceived(null, mockedTransportException);

        //assert
        Queue<IotHubTransportPacket> receivedMessagesQueue = Deencapsulation.getField(transport, "receivedMessagesQueue");
        assertTrue(receivedMessagesQueue.isEmpty());
        new Verifications()
        {
            {
                mockedLogger.LogError(mockedTransportException);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_011: [If this function is called while the connection status is DISCONNECTED, this function shall do nothing.]
    @Test
    public void onConnectionLostWhileDisconnectedDoesNothing()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "connectionStatus", IotHubConnectionStatus.DISCONNECTED);
        Deencapsulation.setField(transport, "iotHubTransportConnection", mockedIotHubTransportConnection);
        new NonStrictExpectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "handleDisconnection", new Class[] {TransportException.class}, mockedTransportException);
            }
        };

        //act
        transport.onConnectionLost(mockedTransportException, "");

        //assert
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "handleDisconnection", new Class[] {TransportException.class}, mockedTransportException);
                times = 0;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_078: [If this function is called with a connection id that is not the same
    // as the current connection id, this function shall do nothing.]
    @Test
    public void onConnectionLostWithWrongConnectionIdDoesNothing()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        final String expectedConnectionId = "1234";
        Deencapsulation.setField(transport, "iotHubTransportConnection", mockedIotHubTransportConnection);

        new Expectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "handleDisconnection", new Class[] {TransportException.class}, mockedTransportException);
                times = 0;

                mockedIotHubTransportConnection.getConnectionId();
                result = expectedConnectionId;
            }
        };

        //act
        transport.onConnectionLost(mockedTransportException, "not the expected connection id");

        //assert
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "handleDisconnection", new Class[] {TransportException.class}, mockedTransportException);
                times = 0;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_012: [If this function is called with a TransportException, this function shall call handleDisconnection with that exception.]
    @Test
    public void onConnectionLostWithTransportExceptionCallsHandleDisconnection()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        final IOException nonTransportException = new IOException();
        final String expectedConnectionId = "1234";
        Deencapsulation.setField(transport, "connectionStatus", IotHubConnectionStatus.CONNECTED);
        Deencapsulation.setField(transport, "iotHubTransportConnection", mockedIotHubTransportConnection);

        new Expectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "handleDisconnection", new Class[] {TransportException.class}, mockedTransportException);

                mockedIotHubTransportConnection.getConnectionId();
                result = expectedConnectionId;
            }
        };

        //act
        transport.onConnectionLost(mockedTransportException, expectedConnectionId);

        //assert
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "handleDisconnection", new Class[] {TransportException.class}, mockedTransportException);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_013: [If this function is called with any other type of exception, this function shall call handleDisconnection with that exception as the inner exception in a new TransportException.]
    @Test
    public void onConnectionLostWithOtherExceptionType()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        final IOException nonTransportException = new IOException();
        Deencapsulation.setField(transport, "connectionStatus", IotHubConnectionStatus.CONNECTED);
        Deencapsulation.setField(transport, "iotHubTransportConnection", mockedIotHubTransportConnection);
        final String expectedConnectionId = "1234";

        new Expectations(IotHubTransport.class)
        {
            {
                new TransportException(nonTransportException);
                result = mockedTransportException;

                Deencapsulation.invoke(transport, "handleDisconnection", new Class[] {TransportException.class}, mockedTransportException);

                mockedIotHubTransportConnection.getConnectionId();
                result = expectedConnectionId;
            }
        };

        //act
        transport.onConnectionLost(nonTransportException, expectedConnectionId);

        //assert
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "handleDisconnection", new Class[] {TransportException.class}, mockedTransportException);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_014: [If the provided connectionId is associated with the current connection, This function shall invoke updateStatus with status CONNECTED, change reason CONNECTION_OK and a null throwable.]
    @Test
    public void onConnectionEstablishedCallsUpdateStatus()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        final String expectedConnectionId = "1234";
        Deencapsulation.setField(transport, "iotHubTransportConnection", mockedIotHubTransportConnection);

        new Expectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "updateStatus",
                        new Class[] {IotHubConnectionStatus.class, IotHubConnectionStatusChangeReason.class, Throwable.class},
                        CONNECTED, CONNECTION_OK, null);
                mockedIotHubTransportConnection.getConnectionId();
                result = expectedConnectionId;
            }
        };

        //act
        transport.onConnectionEstablished(expectedConnectionId);

        //assert
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "updateStatus",
                        new Class[] {IotHubConnectionStatus.class, IotHubConnectionStatusChangeReason.class, Throwable.class},
                        CONNECTED, CONNECTION_OK, null);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_015: [If the provided list of configs is null or empty, this function shall throw an IllegalArgumentException.]
    @Test (expected = IllegalArgumentException.class)
    public void openThrowsForNullConfigList() throws DeviceClientException
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);

        //act
        transport.open(null);
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_015: [If the provided list of configs is null or empty, this function shall throw an IllegalArgumentException.]
    @Test (expected = IllegalArgumentException.class)
    public void openThrowsForEmptyConfigList() throws DeviceClientException
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);

        //act
        transport.open(new ArrayList<DeviceClientConfig>());
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_016: [If the connection status of this object is DISCONNECTED_RETRYING, this function shall throw a TransportException.]
    @Test (expected = TransportException.class)
    public void openThrowsIfConnectionStatusIsDisconnectedRetrying() throws DeviceClientException
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "connectionStatus", DISCONNECTED_RETRYING);
        Collection<DeviceClientConfig> configs = new ArrayList<>();
        configs.add(mockedConfig);

        //act
        transport.open(configs);
    }


    //Tests_SRS_IOTHUBTRANSPORT_34_018: [If the saved SAS token has expired, this function shall throw a SecurityException.]
    @Test (expected = SecurityException.class)
    public void openThrowsIfSasTokenExpired() throws DeviceClientException
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "connectionStatus", DISCONNECTED);
        Collection<DeviceClientConfig> configs = new ArrayList<>();
        configs.add(mockedConfig);

        new Expectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "isSasTokenExpired");
                result = true;
            }
        };

        //act
        transport.open(configs);
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_019: [This function shall open the invoke the method openConnection.]
    @Test
    public void openCallsOpenConnection() throws DeviceClientException
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "connectionStatus", DISCONNECTED);
        Collection<DeviceClientConfig> configs = new ArrayList<>();
        configs.add(mockedConfig);

        new Expectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "isSasTokenExpired");
                result = false;

                Deencapsulation.invoke(transport, "openConnection");
            }
        };

        //act
        transport.open(configs);

        //assert
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "openConnection");
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_017: [If the connection status of this object is CONNECTED, this function shall do nothing.]
    @Test
    public void openDoesNothingIfConnectionStatusIsConnected() throws DeviceClientException
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "connectionStatus", CONNECTED);
        Collection<DeviceClientConfig> configs = new ArrayList<>();
        configs.add(mockedConfig);

        new NonStrictExpectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "openConnection");
                times = 0;
            }
        };

        //act
        transport.open(configs);

        //assert
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "openConnection");
                times = 0;
            }
        };
    }


    //Tests_SRS_IOTHUBTRANSPORT_34_026: [If the supplied reason is null, this function shall throw an IllegalArgumentException.]
    @Test (expected = IllegalArgumentException.class)
    public void closeThrowsForNullReason() throws DeviceClientException
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);

        //act
        transport.close(null, mockedTransportException);
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_021: [This function shall move all waiting messages to the callback queue with status MESSAGE_CANCELLED_ONCLOSE.]
    //Tests_SRS_IOTHUBTRANSPORT_34_022: [This function shall move all in progress messages to the callback queue with status MESSAGE_CANCELLED_ONCLOSE.]
    //Tests_SRS_IOTHUBTRANSPORT_34_023: [This function shall invoke all callbacks.]
    //Tests_SRS_IOTHUBTRANSPORT_34_024: [This function shall close the connection.]
    //Tests_SRS_IOTHUBTRANSPORT_34_025: [This function shall invoke updateStatus with status DISCONNECTED and the supplied reason and cause.]
    @Test
    public void closeMovesAllWaitingAndInProgressMessagesToCallbackQueueWithStatusMessageCancelledOnClose() throws DeviceClientException
    {
        //arrange
        final Queue<IotHubTransportPacket> waitingPacketsQueue = new ConcurrentLinkedQueue<>();
        final Map<String, IotHubTransportPacket> inProgressPackets = new ConcurrentHashMap<>();
        final Queue<IotHubTransportPacket> callbackPacketsQueue = new ConcurrentLinkedQueue<>();
        waitingPacketsQueue.add(mockedPacket);
        waitingPacketsQueue.add(mockedPacket);
        inProgressPackets.put("1", mockedPacket);
        inProgressPackets.put("2", mockedPacket);


        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "connectionStatus", CONNECTED);
        Deencapsulation.setField(transport, "waitingPacketsQueue", waitingPacketsQueue);
        Deencapsulation.setField(transport, "inProgressPackets", inProgressPackets);
        Deencapsulation.setField(transport, "callbackPacketsQueue", callbackPacketsQueue);
        Deencapsulation.setField(transport, "taskScheduler", mockedScheduledExecutorService);
        Deencapsulation.setField(transport, "iotHubTransportConnection", mockedIotHubTransportConnection);

        new Expectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "invokeCallbacks");

                mockedPacket.setStatus(IotHubStatusCode.MESSAGE_CANCELLED_ONCLOSE);
                times = 4;

                Deencapsulation.invoke(transport, "updateStatus",
                        new Class[] {IotHubConnectionStatus.class, IotHubConnectionStatusChangeReason.class, Throwable.class},
                        DISCONNECTED, RETRY_EXPIRED, mockedTransportException);
            }
        };

        //act
        transport.close(RETRY_EXPIRED, mockedTransportException);

        //assert
        assertEquals(4, callbackPacketsQueue.size());
        while (!callbackPacketsQueue.isEmpty())
        {
            assertEquals(mockedPacket, callbackPacketsQueue.poll());
        }
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "invokeCallbacks");
                times = 1;

                mockedIotHubTransportConnection.close();
                times = 1;

                Deencapsulation.invoke(transport, "updateStatus",
                        new Class[] {IotHubConnectionStatus.class, IotHubConnectionStatusChangeReason.class, Throwable.class},
                        DISCONNECTED, RETRY_EXPIRED, mockedTransportException);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_020: [If this object's connection status is DISCONNECTED, this function shall do nothing.]
    @Test
    public void closeWhenDisconnectedDoesNothing() throws DeviceClientException
    {
        //arrange
        final Queue<IotHubTransportPacket> waitingPacketsQueue = new ConcurrentLinkedQueue<>();
        final Map<String, IotHubTransportPacket> inProgressPackets = new ConcurrentHashMap<>();
        final Queue<IotHubTransportPacket> callbackPacketsQueue = new ConcurrentLinkedQueue<>();
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "connectionStatus", DISCONNECTED);
        Deencapsulation.setField(transport, "waitingPacketsQueue", waitingPacketsQueue);
        Deencapsulation.setField(transport, "inProgressPackets", inProgressPackets);
        Deencapsulation.setField(transport, "callbackPacketsQueue", callbackPacketsQueue);
        Deencapsulation.setField(transport, "taskScheduler", mockedScheduledExecutorService);
        Deencapsulation.setField(transport, "iotHubTransportConnection", mockedIotHubTransportConnection);

        new Expectations(IotHubTransport.class)
        {
            {
                mockedPacket.setStatus(IotHubStatusCode.MESSAGE_CANCELLED_ONCLOSE);
                times = 0;
            }
        };

        //act
        transport.close(RETRY_EXPIRED, mockedTransportException);

        //assert
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "invokeCallbacks");
                times = 0;

                mockedIotHubTransportConnection.close();
                times = 0;

                Deencapsulation.invoke(transport, "updateStatus",
                        new Class[] {IotHubConnectionStatus.class, IotHubConnectionStatusChangeReason.class, Throwable.class},
                        DISCONNECTED, RETRY_EXPIRED, mockedTransportException);
                times = 0;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_032: [If the provided exception is not a TransportException, this function shall return COMMUNICATION_ERROR.]
    @Test
    public void exceptionToStatusChangeReasonWithNonTransportException()
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);

        //act
        IotHubConnectionStatusChangeReason reason = Deencapsulation.invoke(transport, "exceptionToStatusChangeReason", new Class[] {Throwable.class}, new IOException());

        //assert
        assertEquals(COMMUNICATION_ERROR, reason);
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_033: [If the provided exception is a retryable TransportException, this function shall return NO_NETWORK.]
    @Test
    public void exceptionToStatusChangeReasonWithRetryableTransportException()
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);

        new NonStrictExpectations()
        {
            {
                mockedTransportException.isRetryable();
                result = true;
            }
        };

        //act
        IotHubConnectionStatusChangeReason reason = Deencapsulation.invoke(transport, "exceptionToStatusChangeReason", new Class[] {Throwable.class}, mockedTransportException);

        //assert
        assertEquals(NO_NETWORK, reason);
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_034: [If the provided exception is a TransportException that isn't retryable and the saved sas token has expired, this function shall return EXPIRED_SAS_TOKEN.]
    @Test
    public void exceptionToStatusChangeReasonSasTokenExpired()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);

        new NonStrictExpectations(IotHubTransport.class)
        {
            {
                mockedTransportException.isRetryable();
                result = false;

                Deencapsulation.invoke(transport, "isSasTokenExpired");
                result = true;
            }
        };

        //act
        IotHubConnectionStatusChangeReason reason = Deencapsulation.invoke(transport, "exceptionToStatusChangeReason", new Class[] {Throwable.class}, mockedTransportException);

        //assert
        assertEquals(EXPIRED_SAS_TOKEN, reason);
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_035: [If the provided exception is a TransportException that isn't retryable and the saved sas token has not expired, this function shall return BAD_CREDENTIAL.]
    @Test
    public void exceptionToStatusChangeReasonBadCredential()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);

        new NonStrictExpectations(IotHubTransport.class)
        {
            {
                mockedTransportException.isRetryable();
                result = false;

                Deencapsulation.invoke(transport, "isSasTokenExpired");
                result = false;
            }
        };

        //act
        IotHubConnectionStatusChangeReason reason = Deencapsulation.invoke(transport, "exceptionToStatusChangeReason", new Class[] {Throwable.class}, mockedTransportException);

        //assert
        assertEquals(BAD_CREDENTIAL, reason);
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_035: [If the default config's protocol is HTTPS, this function shall set this object's iotHubTransportConnection to a new HttpsIotHubConnection object.]
    //Tests_SRS_IOTHUBTRANSPORT_34_038: [This function shall set this object as the listener of the iotHubTransportConnection object.]
    //Tests_SRS_IOTHUBTRANSPORT_34_039: [This function shall open the iotHubTransportConnection object with the saved list of configs.]
    //Tests_SRS_IOTHUBTRANSPORT_34_040: [This function shall invoke the method updateStatus with status CONNECTED, reason CONNECTION_OK, and a null throwable.]
    @Test
    public void openConnectionWithHttp()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        final Queue<DeviceClientConfig> configs = new ConcurrentLinkedQueue<>();
        configs.add(mockedConfig);
        Deencapsulation.setField(transport, "deviceClientConfigs", configs);
        new Expectations(IotHubTransport.class)
        {
            {
                mockedConfig.getProtocol();
                result = IotHubClientProtocol.HTTPS;

                new HttpsIotHubConnection(mockedConfig);
                result = mockedHttpsIotHubConnection;
            }
        };

        //act
        Deencapsulation.invoke(transport, "openConnection");

        //assert
        new Verifications()
        {
            {
                mockedHttpsIotHubConnection.setListener(transport);
                times = 1;

                mockedHttpsIotHubConnection.open(configs);
                times = 1;

                Deencapsulation.invoke(transport, "updateStatus",
                        new Class[] {IotHubConnectionStatus.class, IotHubConnectionStatusChangeReason.class, Throwable.class},
                        CONNECTED, CONNECTION_OK, null);
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_036: [If the default config's protocol is MQTT or MQTT_WS, this function shall set this object's iotHubTransportConnection to a new MqttIotHubConnection object.]
    @Test
    public void openConnectionWithMqtt() throws TransportException
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        final Queue<DeviceClientConfig> configs = new ConcurrentLinkedQueue<>();
        configs.add(mockedConfig);
        Deencapsulation.setField(transport, "deviceClientConfigs", configs);
        new Expectations(IotHubTransport.class)
        {
            {
                mockedConfig.getProtocol();
                result = IotHubClientProtocol.MQTT;

                new MqttIotHubConnection(mockedConfig);
                result = mockedMqttIotHubConnection;
            }
        };

        //act
        Deencapsulation.invoke(transport, "openConnection");

        //assert
        new Verifications()
        {
            {
                mockedMqttIotHubConnection.setListener(transport);
                times = 1;

                mockedMqttIotHubConnection.open(configs);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_036: [If the default config's protocol is MQTT or MQTT_WS, this function shall set this object's iotHubTransportConnection to a new MqttIotHubConnection object.]
    @Test
    public void openConnectionWithMqttWS() throws TransportException
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        final Queue<DeviceClientConfig> configs = new ConcurrentLinkedQueue<>();
        configs.add(mockedConfig);
        Deencapsulation.setField(transport, "deviceClientConfigs", configs);
        new Expectations(IotHubTransport.class)
        {
            {
                mockedConfig.getProtocol();
                result = IotHubClientProtocol.MQTT_WS;

                new MqttIotHubConnection(mockedConfig);
                result = mockedMqttIotHubConnection;
            }
        };

        //act
        Deencapsulation.invoke(transport, "openConnection");

        //assert
        new Verifications()
        {
            {
                mockedMqttIotHubConnection.setListener(transport);
                times = 1;

                mockedMqttIotHubConnection.open(configs);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_037: [If the default config's protocol is AMQPS or AMQPS_WS, this function shall set this object's iotHubTransportConnection to a new AmqpsIotHubConnection object.]
    @Test
    public void openConnectionWithAmqps() throws TransportException
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        final Queue<DeviceClientConfig> configs = new ConcurrentLinkedQueue<>();
        configs.add(mockedConfig);
        Deencapsulation.setField(transport, "deviceClientConfigs", configs);
        new Expectations(IotHubTransport.class)
        {
            {
                mockedConfig.getProtocol();
                result = IotHubClientProtocol.AMQPS;

                new AmqpsIotHubConnection(mockedConfig);
                result = mockedAmqpsIotHubConnection;
            }
        };

        //act
        Deencapsulation.invoke(transport, "openConnection");

        //assert
        new Verifications()
        {
            {
                mockedAmqpsIotHubConnection.setListener(transport);
                times = 1;

                mockedAmqpsIotHubConnection.open(configs);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_037: [If the default config's protocol is AMQPS or AMQPS_WS, this function shall set this object's iotHubTransportConnection to a new AmqpsIotHubConnection object.]
    @Test
    public void openConnectionWithAmqpsWS() throws TransportException
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        final Queue<DeviceClientConfig> configs = new ConcurrentLinkedQueue<>();
        configs.add(mockedConfig);
        Deencapsulation.setField(transport, "deviceClientConfigs", configs);
        new Expectations(IotHubTransport.class)
        {
            {
                mockedConfig.getProtocol();
                result = IotHubClientProtocol.AMQPS_WS;

                new AmqpsIotHubConnection(mockedConfig);
                result = mockedAmqpsIotHubConnection;
            }
        };

        //act
        Deencapsulation.invoke(transport, "openConnection");

        //assert
        new Verifications()
        {
            {
                mockedAmqpsIotHubConnection.setListener(transport);
                times = 1;

                mockedAmqpsIotHubConnection.open(configs);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_043: [This function return true if and only if there are no packets in the waiting queue, in progress, or in the callbacks queue.]
    @Test
    public void isEmptyReturnsTrueIfAllQueuesEmpty()
    {
        //arrange
        final Queue<IotHubTransportPacket> waitingPacketsQueue = new ConcurrentLinkedQueue<>();
        final Map<String, IotHubTransportPacket> inProgressPackets = new ConcurrentHashMap<>();
        final Queue<IotHubTransportPacket> callbackPacketsQueue = new ConcurrentLinkedQueue<>();
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "waitingPacketsQueue", waitingPacketsQueue);
        Deencapsulation.setField(transport, "inProgressPackets", inProgressPackets);
        Deencapsulation.setField(transport, "callbackPacketsQueue", callbackPacketsQueue);

        //act
        boolean isEmpty = transport.isEmpty();

        //assert
        assertTrue(isEmpty);
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_043: [This function return true if and only if there are no packets in the waiting queue, in progress, or in the callbacks queue.]
    @Test
    public void isEmptyReturnsFalseIfWaitingQueueNotEmpty()
    {
        //arrange
        final Queue<IotHubTransportPacket> waitingPacketsQueue = new ConcurrentLinkedQueue<>();
        final Map<String, IotHubTransportPacket> inProgressPackets = new ConcurrentHashMap<>();
        final Queue<IotHubTransportPacket> callbackPacketsQueue = new ConcurrentLinkedQueue<>();
        waitingPacketsQueue.add(mockedPacket);

        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "waitingPacketsQueue", waitingPacketsQueue);
        Deencapsulation.setField(transport, "inProgressPackets", inProgressPackets);
        Deencapsulation.setField(transport, "callbackPacketsQueue", callbackPacketsQueue);

        //act
        boolean isEmpty = transport.isEmpty();

        //assert
        assertFalse(isEmpty);
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_043: [This function return true if and only if there are no packets in the waiting queue, in progress, or in the callbacks queue.]
    @Test
    public void isEmptyReturnsFalseIfInProgressMapNotEmpty()
    {
        //arrange
        final Queue<IotHubTransportPacket> waitingPacketsQueue = new ConcurrentLinkedQueue<>();
        final Map<String, IotHubTransportPacket> inProgressPackets = new ConcurrentHashMap<>();
        final Queue<IotHubTransportPacket> callbackPacketsQueue = new ConcurrentLinkedQueue<>();
        inProgressPackets.put("asdf", mockedPacket);

        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "waitingPacketsQueue", waitingPacketsQueue);
        Deencapsulation.setField(transport, "inProgressPackets", inProgressPackets);
        Deencapsulation.setField(transport, "callbackPacketsQueue", callbackPacketsQueue);

        //act
        boolean isEmpty = transport.isEmpty();

        //assert
        assertFalse(isEmpty);
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_043: [This function return true if and only if there are no packets in the waiting queue, in progress, or in the callbacks queue.]
    @Test
    public void isEmptyReturnsFalseIfCallbackQueueNotEmpty()
    {
        //arrange
        final Queue<IotHubTransportPacket> waitingPacketsQueue = new ConcurrentLinkedQueue<>();
        final Map<String, IotHubTransportPacket> inProgressPackets = new ConcurrentHashMap<>();
        final Queue<IotHubTransportPacket> callbackPacketsQueue = new ConcurrentLinkedQueue<>();
        callbackPacketsQueue.add(mockedPacket);

        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "waitingPacketsQueue", waitingPacketsQueue);
        Deencapsulation.setField(transport, "inProgressPackets", inProgressPackets);
        Deencapsulation.setField(transport, "callbackPacketsQueue", callbackPacketsQueue);

        //act
        boolean isEmpty = transport.isEmpty();

        //assert
        assertFalse(isEmpty);
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_044: [This function shall return if the provided start time was long enough ago that it has passed the device operation timeout threshold.]
    @Test
    public void hasOperationTimedOutTrue()
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);
        new NonStrictExpectations()
        {
            {
                mockedConfig.getOperationTimeout();
                result = 0;
            }
        };

        //act
        boolean hasTimedOut = Deencapsulation.invoke(transport, "hasOperationTimedOut", 1L);

        //assert
        assertTrue(hasTimedOut);
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_077: [If the provided start time is 0, this function shall return false.]
    @Test
    public void hasOperationTimedOutReturnsFalseIfProvidedTimeIsZero()
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);
        new NonStrictExpectations()
        {
            {
                mockedConfig.getOperationTimeout();
                result = 0;
            }
        };

        //act
        boolean hasTimedOut = Deencapsulation.invoke(transport, "hasOperationTimedOut", 0L);

        //assert
        assertFalse(hasTimedOut);
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_044: [This function shall return if the provided start time was long enough ago that it has passed the device operation timeout threshold.]
    @Test
    public void hasOperationTimedOutFalse()
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);
        new NonStrictExpectations()
        {
            {
                mockedConfig.getOperationTimeout();
                result = Long.MAX_VALUE;
            }
        };

        //act
        boolean hasTimedOut = Deencapsulation.invoke(transport, "hasOperationTimedOut", 0L);

        //assert
        assertFalse(hasTimedOut);
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_041: [If this object's connection state is DISCONNECTED, this function shall throw an IllegalStateException.]
    @Test (expected = IllegalStateException.class)
    public void addMessageThrowsIfDisconnected()
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "connectionStatus", DISCONNECTED);

        //act
        transport.addMessage(mockedMessage, mockedEventCallback, new Object());
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_042: [This function shall build a transport packet from the provided message, callback, and context and then add that packet to the waiting queue.]
    @Test
    public void addMessageAddsMessage()
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "connectionStatus", CONNECTED);
        Queue<IotHubTransportPacket> waitingPacketsQueue = new ConcurrentLinkedQueue<>();
        Deencapsulation.setField(transport, "waitingPacketsQueue", waitingPacketsQueue);

        new NonStrictExpectations()
        {
            {
                new IotHubTransportPacket(mockedMessage, mockedEventCallback, any, null, anyLong);
                result = mockedPacket;
            }
        };

        //act
        transport.addMessage(mockedMessage, mockedEventCallback, new Object());

        //assert
        assertEquals(1, waitingPacketsQueue.size());
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_043: [If the connection status of this object is not CONNECTED, this function shall do nothing]
    @Test
    public void sendMessagesDoesNothingIfNotConnected()
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "connectionStatus", DISCONNECTED);
        Queue<IotHubTransportPacket> waitingPacketsQueue = new ConcurrentLinkedQueue<>();
        waitingPacketsQueue.add(mockedPacket);
        Deencapsulation.setField(transport, "waitingPacketsQueue", waitingPacketsQueue);

        //act
        transport.sendMessages();

        //assert
        assertFalse(waitingPacketsQueue.isEmpty());
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_044: [This function continue to dequeue packets saved in the waiting
    // queue and send them until connection status isn't CONNECTED or until 10 messages have been sent]
    @Test
    public void sendMessagesSendsMessages()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        final int MAX_MESSAGES_TO_SEND_PER_THREAD = Deencapsulation.getField(transport, "MAX_MESSAGES_TO_SEND_PER_THREAD");
        Deencapsulation.setField(transport, "connectionStatus", CONNECTED);
        Queue<IotHubTransportPacket> waitingPacketsQueue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < MAX_MESSAGES_TO_SEND_PER_THREAD + 1; i++)
        {
            waitingPacketsQueue.add(mockedPacket);
        }

        Deencapsulation.setField(transport, "waitingPacketsQueue", waitingPacketsQueue);

        new Expectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "sendPacket", new Class[] {IotHubTransportPacket.class}, mockedPacket);
            }
        };

        //act
        transport.sendMessages();

        //assert
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "sendPacket", new Class[] {IotHubTransportPacket.class}, mockedPacket);
                times = MAX_MESSAGES_TO_SEND_PER_THREAD;
            }
        };
        assertEquals(1, waitingPacketsQueue.size());
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_045: [This function shall dequeue each packet in the callback queue and execute
    // their saved callback with their saved status and context]
    @Test
    public void invokeCallbacksInvokesAllCallbacks()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Queue<IotHubTransportPacket> callbackPacketsQueue = new ConcurrentLinkedQueue<>();
        callbackPacketsQueue.add(mockedPacket);
        callbackPacketsQueue.add(mockedPacket);
        callbackPacketsQueue.add(mockedPacket);
        Deencapsulation.setField(transport, "callbackPacketsQueue", callbackPacketsQueue);
        final Object context = new Object();
        new NonStrictExpectations()
        {
            {
                mockedPacket.getCallback();
                result = mockedEventCallback;

                mockedPacket.getContext();
                result = context;

                mockedPacket.getStatus();
                result = mockedStatus;
            }
        };

        //act
        transport.invokeCallbacks();

        //assert
        assertTrue(callbackPacketsQueue.isEmpty());
        new Verifications()
        {
            {
                mockedEventCallback.execute(mockedStatus, context);
                times = 3;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_046: [If this object's connection status is not CONNEECTED, this function shall do nothing.]
    @Test
    public void handleMessageDoesNothingIfNotConnected() throws DeviceClientException
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "connectionStatus", DISCONNECTED);
        Queue<IotHubTransportMessage> receivedMessagesQueue = new ConcurrentLinkedQueue<>();
        receivedMessagesQueue.add(mockedTransportMessage);
        receivedMessagesQueue.add(mockedTransportMessage);
        Deencapsulation.setField(transport, "receivedMessagesQueue", receivedMessagesQueue);

        Deencapsulation.setField(transport, "iotHubTransportConnection", mockedHttpsIotHubConnection);

        //act
        transport.handleMessage();

        //assert
        assertEquals(2, receivedMessagesQueue.size());
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "addReceivedMessagesOverHttpToReceivedQueue");
                times = 0;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_047: [If this object's connection status is CONNECTED and is using HTTPS,
    // this function shall invoke addReceivedMessagesOverHttpToReceivedQueue.]
    @Test
    public void handleMessageChecksForHttpMessages() throws DeviceClientException
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "connectionStatus", CONNECTED);
        Queue<IotHubTransportMessage> receivedMessagesQueue = new ConcurrentLinkedQueue<>();
        receivedMessagesQueue.add(mockedTransportMessage);
        receivedMessagesQueue.add(mockedTransportMessage);
        Deencapsulation.setField(transport, "receivedMessagesQueue", receivedMessagesQueue);

        Deencapsulation.setField(transport, "iotHubTransportConnection", mockedHttpsIotHubConnection);

        new Expectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "acknowledgeReceivedMessage", mockedTransportMessage);

                Deencapsulation.invoke(transport, "addReceivedMessagesOverHttpToReceivedQueue");
            }
        };

        //act
        transport.handleMessage();

        //assert
        assertEquals(1, receivedMessagesQueue.size());
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "addReceivedMessagesOverHttpToReceivedQueue");
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_048: [If this object's connection status is CONNECTED and there is a
    // received message in the queue, this function shall acknowledge the received message
    @Test
    public void handleMessageAcknowledgesAReceivedMessages() throws DeviceClientException
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "connectionStatus", CONNECTED);
        Queue<IotHubTransportMessage> receivedMessagesQueue = new ConcurrentLinkedQueue<>();
        receivedMessagesQueue.add(mockedTransportMessage);
        receivedMessagesQueue.add(mockedTransportMessage);
        Deencapsulation.setField(transport, "receivedMessagesQueue", receivedMessagesQueue);

        new Expectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "acknowledgeReceivedMessage", mockedTransportMessage);
            }
        };

        //act
        transport.handleMessage();

        //assert
        assertEquals(1, receivedMessagesQueue.size());
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "acknowledgeReceivedMessage", mockedTransportMessage);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_049: [If the provided callback is null, this function shall throw an IllegalArgumentException.]
    @Test (expected = IllegalArgumentException.class)
    public void registerConnectionStateCallbackThrowsForNullCallback()
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);

        //act
        transport.registerConnectionStateCallback(null, new Object());
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_050: [This function shall save the provided callback and context.]
    @Test
    public void registerConnectionStateCallbackSavesProvidedCallbackAndContext()
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);
        final Object context = new Object();

        //act
        transport.registerConnectionStateCallback(mockedIotHubConnectionStateCallback, context);

        //assert
        assertEquals(mockedIotHubConnectionStateCallback, Deencapsulation.getField(transport, "stateCallback"));
        assertEquals(context, Deencapsulation.getField(transport, "stateCallbackContext"));
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_051: [If the provided callback is null, this function shall throw an IllegalArgumentException.]
    @Test(expected = IllegalArgumentException.class)
    public void registerConnectionStatusChangeCallbackThrowsForNullCallback()
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);

        //act
        transport.registerConnectionStatusChangeCallback(null, new Object());
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_052: [This function shall save the provided callback and context.]
    @Test
    public void registerConnectionStatusChangeCallbackSavesProvidedCallbackAndContext()
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);
        final Object context = new Object();

        //act
        transport.registerConnectionStatusChangeCallback(mockedIotHubConnectionStatusChangeCallback, context);

        //assert
        assertEquals(mockedIotHubConnectionStatusChangeCallback, Deencapsulation.getField(transport, "connectionStatusChangeCallback"));
        assertEquals(context, Deencapsulation.getField(transport, "connectionStatusChangeCallbackContext"));
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_053: [This function shall execute the callback associate with the provided
    // transport message with the provided message and its saved callback context.]
    //Tests_SRS_IOTHUBTRANSPORT_34_054: [This function shall send the message callback result along the
    // connection as the ack to the service.]
    @Test
    public void acknowledgeReceivedMessageSendsAck() throws TransportException
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);
        final Object context = new Object();
        Deencapsulation.setField(transport, "iotHubTransportConnection", mockedIotHubTransportConnection);
        new Expectations()
        {
            {
                mockedTransportMessage.getMessageCallback();
                result = mockedMessageCallback;

                mockedTransportMessage.getMessageCallbackContext();
                result = context;

                mockedMessageCallback.execute(mockedTransportMessage, context);
                result = IotHubMessageResult.COMPLETE;
            }
        };

        //act
        Deencapsulation.invoke(transport, "acknowledgeReceivedMessage", mockedTransportMessage);

        //assert
        new Verifications()
        {
            {
                mockedMessageCallback.execute(mockedTransportMessage, context);
                times = 1;

                mockedIotHubTransportConnection.sendMessageResult(mockedTransportMessage, IotHubMessageResult.COMPLETE);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_055: [If an exception is thrown while acknowledging the received message,
    // this function shall add the received message back into the receivedMessagesQueue and then rethrow the exception.]
    @Test
    public void acknowledgeReceivedMessageReQueuesFailedMessages() throws TransportException
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);
        final Object context = new Object();
        Deencapsulation.setField(transport, "iotHubTransportConnection", mockedIotHubTransportConnection);
        new Expectations()
        {
            {
                mockedTransportMessage.getMessageCallback();
                result = mockedMessageCallback;

                mockedTransportMessage.getMessageCallbackContext();
                result = context;

                mockedMessageCallback.execute(mockedTransportMessage, context);
                result = IotHubMessageResult.COMPLETE;

                mockedIotHubTransportConnection.sendMessageResult(mockedTransportMessage, IotHubMessageResult.COMPLETE);
                result = mockedTransportException;
            }
        };

        boolean exceptionRethrown = false;

        //act
        try
        {
            Deencapsulation.invoke(transport, "acknowledgeReceivedMessage", mockedTransportMessage);
        }
        catch (Exception e)
        {
            exceptionRethrown = true;
        }

        //assert
        assertTrue(exceptionRethrown);
        Queue<IotHubTransportMessage> receivedMessagesQueue = Deencapsulation.getField(transport, "receivedMessagesQueue");
        assertEquals(1, receivedMessagesQueue.size());
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_056: [If the saved http transport connection can receive a message, add it to receivedMessagesQueue.]
    @Test
    public void addReceivedMessagesOverHttpToReceivedQueueChecksForHttpMessages() throws TransportException
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "iotHubTransportConnection", mockedHttpsIotHubConnection);

        //act
        Deencapsulation.invoke(transport, "addReceivedMessagesOverHttpToReceivedQueue");

        //assert
        Queue<IotHubTransportMessage> receivedMessagesQueue = Deencapsulation.getField(transport, "receivedMessagesQueue");
        assertEquals(1, receivedMessagesQueue.size());
        new Verifications()
        {
            {
                mockedHttpsIotHubConnection.receiveMessage();
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_057: [This function shall move all packets from inProgressQueue to waiting queue.]
    //Tests_SRS_IOTHUBTRANSPORT_34_058: [This function shall invoke updateStatus with DISCONNECTED_RETRYING, and the provided transportException.]
    //Tests_SRS_IOTHUBTRANSPORT_34_059: [This function shall invoke checkForUnauthorizedException with the provided exception.]
    //Tests_SRS_IOTHUBTRANSPORT_34_060: [This function shall invoke reconnect with the provided exception.]
    @Test
    public void handleDisconnectionClearsInProgressAndReconnects()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);

        new Expectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "exceptionToStatusChangeReason", mockedTransportException);
                result = mockedIotHubConnectionStatusChangeReason;

                Deencapsulation.invoke(transport, "updateStatus",
                        new Class[] {IotHubConnectionStatus.class, IotHubConnectionStatusChangeReason.class, Throwable.class},
                        DISCONNECTED_RETRYING, mockedIotHubConnectionStatusChangeReason, mockedTransportException);

                Deencapsulation.invoke(transport, "checkForUnauthorizedException", mockedTransportException);

                Deencapsulation.invoke(transport, "reconnect", mockedTransportException);
            }
        };

        //act
        Deencapsulation.invoke(transport, "handleDisconnection", mockedTransportException);

        //assert
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "exceptionToStatusChangeReason", mockedTransportException);
                times = 1;

                Deencapsulation.invoke(transport, "updateStatus",
                        new Class[] {IotHubConnectionStatus.class, IotHubConnectionStatusChangeReason.class, Throwable.class},
                        DISCONNECTED_RETRYING, mockedIotHubConnectionStatusChangeReason, mockedTransportException);
                times = 1;

                Deencapsulation.invoke(transport, "checkForUnauthorizedException", mockedTransportException);
                times = 1;

                Deencapsulation.invoke(transport, "reconnect", mockedTransportException);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_061: [This function shall close the saved connection, and then invoke openConnection and return null.]
    @Test
    public void singleReconnectAttemptSuccess() throws TransportException
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "iotHubTransportConnection", mockedIotHubTransportConnection);

        new Expectations(IotHubTransport.class)
        {
            {
                //open and close happen with no exception
                mockedIotHubTransportConnection.close();
                Deencapsulation.invoke(transport, "openConnection");
            }
        };

        //act
        Exception result = Deencapsulation.invoke(transport, "singleReconnectAttempt");

        //assert
        assertNull(result);
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_062: [If an exception is encountered while closing or opening the connection,
    // this function shall invoke checkForUnauthorizedException on that exception and then return it.]
    @Test
    public void singleReconnectAttemptReturnsEncounteredException() throws TransportException
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "iotHubTransportConnection", mockedIotHubTransportConnection);

        new Expectations(IotHubTransport.class)
        {
            {
                //open and close happen with no exception
                mockedIotHubTransportConnection.close();
                result = mockedTransportException;

                Deencapsulation.invoke(transport, "checkForUnauthorizedException", mockedTransportException);
            }
        };

        //act
        Exception result = Deencapsulation.invoke(transport, "singleReconnectAttempt");

        //assert
        assertEquals(mockedTransportException, result);
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "checkForUnauthorizedException", mockedTransportException);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_063: [If the provided transportException is retryable, the packet has not
    // timed out, and the retry policy allows, this function shall schedule a task to add the provided
    // packet to the waiting list after the amount of time determined by the retry policy.]
    @Test
    public void handleMessageExceptionSchedulesRetryIfRetryable()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        final long expectedDelay = 0;
        final long duration = 0;
        Deencapsulation.setField(transport, "taskScheduler", mockedTaskScheduler);
        new Expectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "hasOperationTimedOut", anyLong);
                result = false;

                mockedTransportException.isRetryable();
                result = true;

                mockedConfig.getRetryPolicy();
                result = mockedRetryPolicy;

                mockedRetryPolicy.getRetryDecision(anyInt, mockedTransportException);
                result = mockedRetryDecision;

                mockedRetryDecision.shouldRetry();
                result = true;

                mockedRetryDecision.getDuration();
                result = duration;
            }
        };

        //act
        Deencapsulation.invoke(transport, "handleMessageException", mockedPacket, mockedTransportException);

        //assert
        new Verifications()
        {
            {
                mockedPacket.incrementRetryAttempt();
                times = 1;

                mockedTaskScheduler.schedule((IotHubTransport.MessageRetryRunnable) any, expectedDelay, TimeUnit.MILLISECONDS);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_064: [If the provided transportException is not retryable, the packet has expired,
    // or if the retry policy says to not retry, this function shall add the provided packet to the callback queue.]
    @Test
    public void handleMessageExceptionDoesNotRetryIfDeviceOperationTimedOut()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        final long expectedDelay = 0;
        Deencapsulation.setField(transport, "taskScheduler", mockedTaskScheduler);
        new NonStrictExpectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "hasOperationTimedOut", anyLong);
                result = true;

                mockedIothubServiceException.isRetryable();
                result = true;

                mockedConfig.getRetryPolicy();
                result = mockedRetryPolicy;

                mockedRetryPolicy.getRetryDecision(anyInt, mockedTransportException);
                result = mockedRetryDecision;

                mockedRetryDecision.shouldRetry();
                result = true;

                mockedIothubServiceException.getStatusCode();
                result = mockedStatus;
            }
        };

        //act
        Deencapsulation.invoke(transport, "handleMessageException", mockedPacket, mockedIothubServiceException);

        //assert
        Queue<IotHubTransportPacket> callbackQueue = Deencapsulation.getField(transport, "callbackPacketsQueue");
        assertEquals(1, callbackQueue.size());
        assertEquals(mockedPacket, callbackQueue.poll());
        new Verifications()
        {
            {
                mockedPacket.setStatus(mockedStatus);
                times = 1;

                mockedTaskScheduler.schedule((IotHubTransport.MessageRetryRunnable) any, expectedDelay, TimeUnit.MILLISECONDS);
                times = 0;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_064: [If the provided transportException is not retryable, the packet has expired,
    // or if the retry policy says to not retry, this function shall add the provided packet to the callback queue.]
    @Test
    public void handleMessageExceptionDoesNotRetryIfExceptionIsNotRetryable()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        final long expectedDelay = 0;
        Deencapsulation.setField(transport, "taskScheduler", mockedTaskScheduler);
        new NonStrictExpectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "hasOperationTimedOut", anyLong);
                result = false;

                mockedTransportException.isRetryable();
                result = false;

                mockedConfig.getRetryPolicy();
                result = mockedRetryPolicy;

                mockedRetryPolicy.getRetryDecision(anyInt, mockedTransportException);
                result = mockedRetryDecision;

                mockedRetryDecision.shouldRetry();
                result = true;

                mockedIothubServiceException.getStatusCode();
                result = mockedStatus;
            }
        };

        //act
        Deencapsulation.invoke(transport, "handleMessageException", mockedPacket, mockedIothubServiceException);

        //assert
        Queue<IotHubTransportPacket> callbackQueue = Deencapsulation.getField(transport, "callbackPacketsQueue");
        assertEquals(1, callbackQueue.size());
        assertEquals(mockedPacket, callbackQueue.poll());
        new Verifications()
        {
            {
                mockedPacket.setStatus(mockedStatus);
                times = 1;

                mockedTaskScheduler.schedule((IotHubTransport.MessageRetryRunnable) any, expectedDelay, TimeUnit.MILLISECONDS);
                times = 0;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_064: [If the provided transportException is not retryable, the packet has expired,
    // or if the retry policy says to not retry, this function shall add the provided packet to the callback queue.]
    @Test
    public void handleMessageExceptionDoesNotRetryIfRetryPolicySaysToNotRetry()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        final long expectedDelay = 0;
        Deencapsulation.setField(transport, "taskScheduler", mockedTaskScheduler);
        new NonStrictExpectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "hasOperationTimedOut", anyLong);
                result = false;

                mockedTransportException.isRetryable();
                result = true;

                mockedConfig.getRetryPolicy();
                result = mockedRetryPolicy;

                mockedRetryPolicy.getRetryDecision(anyInt, mockedTransportException);
                result = mockedRetryDecision;

                mockedRetryDecision.shouldRetry();
                result = false;

                mockedIothubServiceException.getStatusCode();
                result = mockedStatus;
            }
        };

        //act
        Deencapsulation.invoke(transport, "handleMessageException", mockedPacket, mockedIothubServiceException);

        //assert
        Queue<IotHubTransportPacket> callbackQueue = Deencapsulation.getField(transport, "callbackPacketsQueue");
        assertEquals(1, callbackQueue.size());
        assertEquals(mockedPacket, callbackQueue.poll());
        new Verifications()
        {
            {
                mockedPacket.setStatus(mockedStatus);
                times = 1;

                mockedTaskScheduler.schedule((IotHubTransport.MessageRetryRunnable) any, expectedDelay, TimeUnit.MILLISECONDS);
                times = 0;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_068: [If the reconnection effort ends because the retry policy said to
    // stop, this function shall invoke close with RETRY_EXPIRED and the last transportException.]
    //Tests_SRS_IOTHUBTRANSPORT_34_065: [If the saved reconnection attempt start time is 0, this function shall 
    // save the current time as the time that reconnection started.]
    //Tests_SRS_IOTHUBTRANSPORT_34_066: [This function shall attempt to reconnect while this object's state is
    // DISCONNECTED_RETRYING, the operation hasn't timed out, and the last transport exception is retryable.]
    @Test
    public void reconnectAttemptsToReconnectUntilRetryPolicyEnds()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "connectionStatus", DISCONNECTED_RETRYING);
        new NonStrictExpectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "hasOperationTimedOut", anyLong);
                result = false;

                mockedTransportException.isRetryable();
                result = true;

                mockedConfig.getRetryPolicy();
                result = mockedRetryPolicy;

                mockedRetryPolicy.getRetryDecision(anyInt, (TransportException) any);
                result = mockedRetryDecision;

                mockedRetryDecision.shouldRetry();
                result = false;

                Deencapsulation.invoke(transport, "close", new Class[] {IotHubConnectionStatusChangeReason.class, Throwable.class}, RETRY_EXPIRED, any);
            }
        };

        //act
        Deencapsulation.invoke(transport, "reconnect", mockedTransportException);

        //assert
        long reconnectionAttemptStartTimeMillis = Deencapsulation.getField(transport, "reconnectionAttemptStartTimeMillis");
        assertTrue(reconnectionAttemptStartTimeMillis > 0);
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "close", new Class[] {IotHubConnectionStatusChangeReason.class, Throwable.class}, RETRY_EXPIRED, any);
                times = 1;
            }
        };
    }


    //Tests_SRS_IOTHUBTRANSPORT_34_069: [If the reconnection effort ends because the reconnection timed out,
    // this function shall invoke close with RETRY_EXPIRED and a DeviceOperationTimeoutException.]
    @Test
    public void reconnectAttemptsToReconnectUntilOperationTimesOut()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "connectionStatus", DISCONNECTED_RETRYING);
        new NonStrictExpectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "hasOperationTimedOut", anyLong);
                result = true;

                mockedTransportException.isRetryable();
                result = true;

                mockedConfig.getRetryPolicy();
                result = mockedRetryPolicy;

                mockedRetryPolicy.getRetryDecision(anyInt, (TransportException) any);
                result = mockedRetryDecision;

                mockedRetryDecision.shouldRetry();
                result = false;

                Deencapsulation.invoke(transport, "close", new Class[] {IotHubConnectionStatusChangeReason.class, Throwable.class}, RETRY_EXPIRED, any);
            }
        };

        //act
        Deencapsulation.invoke(transport, "reconnect", mockedTransportException);

        //assert
        long reconnectionAttemptStartTimeMillis = Deencapsulation.getField(transport, "reconnectionAttemptStartTimeMillis");
        assertTrue(reconnectionAttemptStartTimeMillis > 0);
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "close", new Class[] {IotHubConnectionStatusChangeReason.class, Throwable.class}, RETRY_EXPIRED, any);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_070: [If the reconnection effort ends because a terminal exception is
    // encountered, this function shall invoke close with that terminal exception.]
    @Test
    public void reconnectAttemptsToReconnectUntilExceptionNotRetryable()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "connectionStatus", DISCONNECTED_RETRYING);
        new NonStrictExpectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "hasOperationTimedOut", anyLong);
                result = false;

                mockedTransportException.isRetryable();
                result = false;

                mockedConfig.getRetryPolicy();
                result = mockedRetryPolicy;

                mockedRetryPolicy.getRetryDecision(anyInt, (TransportException) any);
                result = mockedRetryDecision;

                mockedRetryDecision.shouldRetry();
                result = false;

                Deencapsulation.invoke(transport, "close", new Class[] {IotHubConnectionStatusChangeReason.class, Throwable.class}, any, any);

                Deencapsulation.invoke(transport, "exceptionToStatusChangeReason", mockedTransportException);

            }
        };

        //act
        Deencapsulation.invoke(transport, "reconnect", mockedTransportException);

        //assert
        long reconnectionAttemptStartTimeMillis = Deencapsulation.getField(transport, "reconnectionAttemptStartTimeMillis");
        assertTrue(reconnectionAttemptStartTimeMillis > 0);
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "close", new Class[] {IotHubConnectionStatusChangeReason.class, Throwable.class}, any, mockedTransportException);
                times = 1;

                Deencapsulation.invoke(transport, "exceptionToStatusChangeReason", mockedTransportException);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_071: [If an exception is encountered while closing, this function shall invoke
    // updateStatus with DISCONNECTED, COMMUNICATION_ERROR, and the last transport exception.]
    @Test
    public void reconnectUpdatesStatusIfClosingFailed()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "connectionStatus", DISCONNECTED_RETRYING);
        new NonStrictExpectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "hasOperationTimedOut", anyLong);
                result = true;

                mockedTransportException.isRetryable();
                result = true;

                mockedConfig.getRetryPolicy();
                result = mockedRetryPolicy;

                mockedRetryPolicy.getRetryDecision(anyInt, (TransportException) any);
                result = mockedRetryDecision;

                mockedRetryDecision.shouldRetry();
                result = true;

                Deencapsulation.invoke(transport, "close", new Class[] {IotHubConnectionStatusChangeReason.class, Throwable.class}, RETRY_EXPIRED, any);
                result = mockedTransportException;
            }
        };

        //act
        Deencapsulation.invoke(transport, "reconnect", mockedTransportException);

        //assert
        long reconnectionAttemptStartTimeMillis = Deencapsulation.getField(transport, "reconnectionAttemptStartTimeMillis");
        assertTrue(reconnectionAttemptStartTimeMillis > 0);
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "updateStatus", IotHubConnectionStatus.DISCONNECTED, IotHubConnectionStatusChangeReason.COMMUNICATION_ERROR, mockedTransportException);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_28_008:[This function shall set the packet status to MESSAGE_EXPIRED if packet has expired.]
    //Tests_SRS_IOTHUBTRANSPORT_28_009:[This function shall add the expired packet to the Callback Queue.]
    @Test
    public void isMessageValidWithMessageExpired()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        new NonStrictExpectations(IotHubTransport.class)
        {
            {
                mockedPacket.getMessage();
                result = mockedMessage;
                mockedMessage.isExpired();
                result = true;
                Deencapsulation.invoke(transport, "addToCallbackQueue", new Class[] {IotHubTransportPacket.class}, mockedPacket);
                Deencapsulation.invoke(transport, "isSasTokenExpired");
                result = false;
            }
        };

        //act
        boolean ret = Deencapsulation.invoke(transport, "isMessageValid", new Class[] {IotHubTransportPacket.class}, mockedPacket);

        //assert
        assertFalse(ret);
        new Verifications()
        {
            {
                mockedPacket.setStatus(IotHubStatusCode.MESSAGE_EXPIRED);
                times = 1;
                Deencapsulation.invoke(transport, "addToCallbackQueue", new Class[] {IotHubTransportPacket.class}, mockedPacket);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_28_008:[This function shall set the packet status to MESSAGE_EXPIRED if packet has expired.]
    //Tests_SRS_IOTHUBTRANSPORT_28_009:[This function shall add the expired packet to the Callback Queue.]
    //Tests_SRS_IOTHUBTRANSPORT_28_010:[This function shall set the packet status to UNAUTHORIZED if sas token has expired.]
    //Tests_SRS_IOTHUBTRANSPORT_28_011:[This function shall add the packet which sas token has expired to the Callback Queue.]
    @Test
    public void isMessageValidWithMessageNotExpiredAndValidSasToken()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        new NonStrictExpectations(IotHubTransport.class)
        {
            {
                mockedPacket.getMessage();
                result = mockedMessage;
                mockedMessage.isExpired();
                result = false;
                Deencapsulation.invoke(transport, "addToCallbackQueue", new Class[] {IotHubTransportPacket.class}, mockedPacket);
                Deencapsulation.invoke(transport, "isSasTokenExpired");
                result = false;
            }
        };

        //act
        boolean ret = Deencapsulation.invoke(transport, "isMessageValid", new Class[] {IotHubTransportPacket.class}, mockedPacket);

        //assert
        assertTrue(ret);
        new Verifications()
        {
            {
                mockedPacket.setStatus(IotHubStatusCode.MESSAGE_EXPIRED);
                times = 0;
                Deencapsulation.invoke(transport, "addToCallbackQueue", new Class[] {IotHubTransportPacket.class}, mockedPacket);
                times = 0;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_28_010:[This function shall set the packet status to UNAUTHORIZED if sas token has expired.]
    //Tests_SRS_IOTHUBTRANSPORT_28_011:[This function shall add the packet which sas token has expired to the Callback Queue.]
    @Test
    public void isMessageValidWithMessageNotExpiredSasTokenExpired()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        new NonStrictExpectations(IotHubTransport.class)
        {
            {
                mockedPacket.getMessage();
                result = mockedMessage;
                mockedMessage.isExpired();
                result = false;
                Deencapsulation.invoke(transport, "addToCallbackQueue", new Class[] {IotHubTransportPacket.class}, mockedPacket);
                Deencapsulation.invoke(transport, "isSasTokenExpired");
                result = true;
                Deencapsulation.invoke(transport, "updateStatus",
                        new Class[] {IotHubConnectionStatus.class, IotHubConnectionStatusChangeReason.class, Throwable.class},
                        IotHubConnectionStatus.DISCONNECTED, IotHubConnectionStatusChangeReason.EXPIRED_SAS_TOKEN, any);
            }
        };

        //act
        boolean ret = Deencapsulation.invoke(transport, "isMessageValid", new Class[] {IotHubTransportPacket.class}, mockedPacket);

        //assert
        assertFalse(ret);
        new Verifications()
        {
            {
                mockedPacket.setStatus(IotHubStatusCode.UNAUTHORIZED);
                times = 1;
                Deencapsulation.invoke(transport, "addToCallbackQueue", new Class[] {IotHubTransportPacket.class}, mockedPacket);
                times = 1;
                Deencapsulation.invoke(transport, "updateStatus",
                        new Class[] {IotHubConnectionStatus.class, IotHubConnectionStatusChangeReason.class, Throwable.class},
                        IotHubConnectionStatus.DISCONNECTED, IotHubConnectionStatusChangeReason.EXPIRED_SAS_TOKEN, any);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_28_005:[This function shall updated the saved connection status if the connection status has changed.]
    //Tests_SRS_IOTHUBTRANSPORT_28_006:[This function shall invoke all callbacks listening for the state change if the connection status has changed.]
    //Tests_SRS_IOTHUBTRANSPORT_28_007: [This function shall reset currentReconnectionAttempt and reconnectionAttemptStartTimeMillis if connection status is changed to CONNECTED.]
    @Test
    public void updateStatusConnectionStatusChangedToConnected()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "connectionStatus", IotHubConnectionStatus.DISCONNECTED_RETRYING);
        Deencapsulation.setField(transport, "currentReconnectionAttempt", 5);
        Deencapsulation.setField(transport, "reconnectionAttemptStartTimeMillis", 5);
        new Expectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "invokeConnectionStatusChangeCallback",
                        new Class[] {IotHubConnectionStatus.class, IotHubConnectionStatusChangeReason.class, Throwable.class},
                        IotHubConnectionStatus.CONNECTED, IotHubConnectionStatusChangeReason.NO_NETWORK, null);
            }
        };

        //act
        Deencapsulation.invoke(transport, "updateStatus",
                new Class[] {IotHubConnectionStatus.class, IotHubConnectionStatusChangeReason.class, Throwable.class},
                IotHubConnectionStatus.CONNECTED, IotHubConnectionStatusChangeReason.NO_NETWORK, null);

        //assert
        assertEquals(IotHubConnectionStatus.CONNECTED, Deencapsulation.getField(transport, "connectionStatus"));
        assertEquals(0, Deencapsulation.getField(transport, "currentReconnectionAttempt"));
        assertEquals(0L, Deencapsulation.getField(transport, "reconnectionAttemptStartTimeMillis"));
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "invokeConnectionStatusChangeCallback",
                        new Class[] {IotHubConnectionStatus.class, IotHubConnectionStatusChangeReason.class, Throwable.class},
                        IotHubConnectionStatus.CONNECTED, IotHubConnectionStatusChangeReason.NO_NETWORK, null);
                times =1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_28_005:[This function shall updated the saved connection status if the connection status has changed.]
    //Tests_SRS_IOTHUBTRANSPORT_28_006:[This function shall invoke all callbacks listening for the state change if the connection status has changed.]
    //Tests_SRS_IOTHUBTRANSPORT_28_007: [This function shall reset currentReconnectionAttempt and reconnectionAttemptStartTimeMillis if connection status is changed to CONNECTED.]
    @Test
    public void updateStatusConnectionStatusNotChanged()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "connectionStatus", IotHubConnectionStatus.CONNECTED);
        Deencapsulation.setField(transport, "currentReconnectionAttempt", 5);
        new NonStrictExpectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "invokeConnectionStatusChangeCallback",
                        new Class[] {IotHubConnectionStatus.class, IotHubConnectionStatusChangeReason.class, Throwable.class},
                        IotHubConnectionStatus.CONNECTED, null, null);
            }
        };

        //act
        Deencapsulation.invoke(transport, "updateStatus",
                new Class[] {IotHubConnectionStatus.class, IotHubConnectionStatusChangeReason.class, Throwable.class},
                IotHubConnectionStatus.CONNECTED, null, null);

        //assert
        assertEquals(IotHubConnectionStatus.CONNECTED, Deencapsulation.getField(transport, "connectionStatus"));
        assertEquals(5, Deencapsulation.getField(transport, "currentReconnectionAttempt"));
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "invokeConnectionStatusChangeCallback",
                        new Class[] {IotHubConnectionStatus.class, IotHubConnectionStatusChangeReason.class, Throwable.class},
                        IotHubConnectionStatus.CONNECTED, null, null);
                times = 0;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_28_005:[This function shall updated the saved connection status if the connection status has changed.]
    //Tests_SRS_IOTHUBTRANSPORT_28_006:[This function shall invoke all callbacks listening for the state change if the connection status has changed.]
    //Tests_SRS_IOTHUBTRANSPORT_28_007: [This function shall reset currentReconnectionAttempt and reconnectionAttemptStartTimeMillis if connection status is changed to CONNECTED.]
    @Test
    public void updateStatusConnectionStatusChangedToDisconnected()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "connectionStatus", IotHubConnectionStatus.DISCONNECTED_RETRYING);
        Deencapsulation.setField(transport, "currentReconnectionAttempt", 5);
        new Expectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "invokeConnectionStatusChangeCallback",
                        new Class[] {IotHubConnectionStatus.class, IotHubConnectionStatusChangeReason.class, Throwable.class},
                        IotHubConnectionStatus.DISCONNECTED, IotHubConnectionStatusChangeReason.NO_NETWORK, null);
            }
        };

        //act
        Deencapsulation.invoke(transport, "updateStatus",
                new Class[] {IotHubConnectionStatus.class, IotHubConnectionStatusChangeReason.class, Throwable.class},
                IotHubConnectionStatus.DISCONNECTED, IotHubConnectionStatusChangeReason.NO_NETWORK, null);

        //assert
        assertEquals(IotHubConnectionStatus.DISCONNECTED, Deencapsulation.getField(transport, "connectionStatus"));
        assertEquals(5, Deencapsulation.getField(transport, "currentReconnectionAttempt"));
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "invokeConnectionStatusChangeCallback",
                        new Class[] {IotHubConnectionStatus.class, IotHubConnectionStatusChangeReason.class, Throwable.class},
                        IotHubConnectionStatus.DISCONNECTED, IotHubConnectionStatusChangeReason.NO_NETWORK, null);
                times =1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_28_004:[This function shall notify the connection status change callback if the callback is not null]
    @Test
    public void invokeConnectionStatusChangeCallbackWithCallback()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "connectionStatusChangeCallback", mockedIotHubConnectionStatusChangeCallback);

        //act
        Deencapsulation.invoke(transport, "invokeConnectionStatusChangeCallback",
                new Class[] {IotHubConnectionStatus.class, IotHubConnectionStatusChangeReason.class, Throwable.class},
                IotHubConnectionStatus.CONNECTED, IotHubConnectionStatusChangeReason.CONNECTION_OK, new IOException());

        //assert
        new Verifications()
        {
            {
                mockedIotHubConnectionStatusChangeCallback.execute(
                        (IotHubConnectionStatus)any,
                        (IotHubConnectionStatusChangeReason)any,
                        (Throwable)any,
                        any);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_28_004:[This function shall notify the connection status change callback if the callback is not null]
    @Test
    public void invokeConnectionStatusChangeCallbackWithNullCallback()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Deencapsulation.setField(transport, "connectionStatusChangeCallback", null);

        //act
        Deencapsulation.invoke(transport, "invokeConnectionStatusChangeCallback",
                new Class[] {IotHubConnectionStatus.class, IotHubConnectionStatusChangeReason.class, Throwable.class},
                IotHubConnectionStatus.CONNECTED, IotHubConnectionStatusChangeReason.CONNECTION_OK, new IOException());

        //assert

        new Verifications()
        {
            {
                mockedIotHubConnectionStatusChangeCallback.execute(
                        (IotHubConnectionStatus)any,
                        (IotHubConnectionStatusChangeReason)any,
                        (Throwable)any,
                        any);
                times = 0;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_28_003: [This function shall indicate if the device's sas token is expired.]
    @Test
    public void isSasTokenExpiredAuthenticationTypeIsSasTokenAndExpired()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        new NonStrictExpectations()
        {
            {
                mockedConfig.getAuthenticationType();
                result = DeviceClientConfig.AuthType.SAS_TOKEN;
                mockedConfig.getSasTokenAuthentication().isRenewalNecessary();
                result = true;
            }
        };

        //act
        boolean ret = Deencapsulation.invoke(transport, "isSasTokenExpired");

        //assert
        assertTrue(ret);
    }

    //Tests_SRS_IOTHUBTRANSPORT_28_003: [This function shall indicate if the device's sas token is expired.]
    @Test
    public void isSasTokenExpiredAuthenticationTypeIsSasTokenAndNotExpired()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        new NonStrictExpectations()
        {
            {
                mockedConfig.getAuthenticationType();
                result = DeviceClientConfig.AuthType.SAS_TOKEN;
                mockedConfig.getSasTokenAuthentication().isRenewalNecessary();
                result = false;
            }
        };

        //act
        boolean ret = Deencapsulation.invoke(transport, "isSasTokenExpired");

        //assert
        assertFalse(ret);
    }

    //Tests_SRS_IOTHUBTRANSPORT_28_003: [This function shall indicate if the device's sas token is expired.]
    @Test
    public void isSasTokenExpiredAuthenticationTypeNotSasToken()
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        new NonStrictExpectations()
        {
            {
                mockedConfig.getAuthenticationType();
                result = DeviceClientConfig.AuthType.X509_CERTIFICATE;
                mockedConfig.getSasTokenAuthentication().isRenewalNecessary();
                result = true;
            }
        };

        //act
        boolean ret = Deencapsulation.invoke(transport, "isSasTokenExpired");

        //assert
        assertFalse(ret);
    }

    //Tests_SRS_IOTHUBTRANSPORT_28_002: [This function shall add the packet to the callback queue if it has a callback.]
    @Test
    public void addToCallbackQueuePacketHasCallback(@Mocked final IotHubEventCallback mockCallback)
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        new NonStrictExpectations()
        {
            {
                mockedPacket.getCallback();
                result = mockCallback;
            }
        };

        //act
        Deencapsulation.invoke(transport, "addToCallbackQueue", mockedPacket);

        //assert
        Queue<IotHubTransportPacket> callbackPacketsQueue = Deencapsulation.getField(transport, "callbackPacketsQueue");
        assertEquals(1, callbackPacketsQueue.size());
        assertTrue(callbackPacketsQueue.contains(mockedPacket));
    }

    //Tests_SRS_IOTHUBTRANSPORT_28_002: [This function shall add the packet to the callback queue if it has a callback.]
    @Test
    public void addToCallbackQueuePacketNoCallback(@Mocked final IotHubEventCallback mockCallback)
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        new NonStrictExpectations()
        {
            {
                mockedPacket.getCallback();
                result = null;
            }
        };

        //act
        Deencapsulation.invoke(transport, "addToCallbackQueue", mockedPacket);

        //assert
        Queue<IotHubTransportPacket> callbackPacketsQueue = Deencapsulation.getField(transport, "callbackPacketsQueue");
        assertEquals(0, callbackPacketsQueue.size());
    }

    //Tests_SRS_IOTHUBTRANSPORT_28_001: [This function shall set the MqttUnauthorizedException as retryable if the sas token has not expired.]
    @Test
    public void checkForUnauthorizedExceptionInMqttUnauthroizedException()
    {
        //arrange
        final MqttUnauthorizedException testException = new MqttUnauthorizedException();
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        new NonStrictExpectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "isSasTokenExpired");
                result = false;
            }
        };

        //act
        Deencapsulation.invoke(transport, "checkForUnauthorizedException", testException);

        //assert
        new Verifications()
        {
            {
                testException.setRetryable(true);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_28_001: [This function shall set the MqttUnauthorizedException, UnauthorizedException or
    //AmqpUnauthorizedAccessException as retryable if the sas token has not expired.]
    @Test
    public void checkForUnauthorizedExceptionInUnauthorizedException()
    {
        //arrange
        final UnauthorizedException testException = new UnauthorizedException();
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        new NonStrictExpectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "isSasTokenExpired");
                result = false;
            }
        };

        //act
        Deencapsulation.invoke(transport, "checkForUnauthorizedException", testException);

        //assert
        new Verifications()
        {
            {
                testException.setRetryable(true);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_28_001: [This function shall set the MqttUnauthorizedException, UnauthorizedException or
    //AmqpUnauthorizedAccessException as retryable if the sas token has not expired.]
    @Test
    public void checkForUnauthorizedExceptionInAmqpUnauthorizedAccessException()
    {
        //arrange
        final AmqpUnauthorizedAccessException testException = new AmqpUnauthorizedAccessException();
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        new NonStrictExpectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "isSasTokenExpired");
                result = false;
            }
        };

        //act
        Deencapsulation.invoke(transport, "checkForUnauthorizedException", testException);

        //assert
        new Verifications()
        {
            {
                testException.setRetryable(true);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_28_001: [This function shall set the MqttUnauthorizedException, UnauthorizedException or
    //AmqpUnauthorizedAccessException as retryable if the sas token has not expired.]
    @Test
    public void checkForUnauthorizedExceptionWithExpiredSASToken()
    {
        //arrange
        final UnauthorizedException testException = new UnauthorizedException();
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        new NonStrictExpectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "isSasTokenExpired");
                result = true;
            }
        };

        //act
        Deencapsulation.invoke(transport, "checkForUnauthorizedException", testException);

        //assert
        new Verifications()
        {
            {
                testException.setRetryable(true);
                times = 0;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_28_001: [This function shall set the MqttUnauthorizedException, UnauthorizedException or
    //AmqpUnauthorizedAccessException as retryable if the sas token has not expired.]
    @Test
    public void checkForUnauthorizedExceptionWithOtherTransportException()
    {
        //arrange
        final TransportException testException = new TransportException();
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        new NonStrictExpectations(IotHubTransport.class)
        {
            {
                Deencapsulation.invoke(transport, "isSasTokenExpired");
                result = true;
            }
        };

        //act
        Deencapsulation.invoke(transport, "checkForUnauthorizedException", testException);

        //assert
        new Verifications()
        {
            {
                testException.setRetryable(true);
                times = 0;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_072: [This function shall check if the provided message should expect an ACK or not.]
    //Tests_SRS_IOTHUBTRANSPORT_34_073: [This function shall send the provided message over the saved connection
    // and save the response code.]
    @Test
    public void sendPacketHappyPathWithAck() throws TransportException
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);
        Map<String, IotHubTransportPacket> inProgressMessages = new HashMap<>();
        Deencapsulation.setField(transport, "inProgressPackets", inProgressMessages);
        Deencapsulation.setField(transport, "iotHubTransportConnection", mockedHttpsIotHubConnection);
        new NonStrictExpectations()
        {
            {
                mockedPacket.getMessage();
                result = mockedTransportMessage;

                mockedTransportMessage.isMessageAckNeeded((IotHubClientProtocol) any);
                result = true;

                mockedHttpsIotHubConnection.sendMessage((Message) any);
                result = IotHubStatusCode.OK_EMPTY;
            }
        };

        //act
        Deencapsulation.invoke(transport, "sendPacket", mockedPacket);

        //assert
        assertEquals(1, inProgressMessages.size());
    }


    //Tests_SRS_IOTHUBTRANSPORT_34_074: [If the response from sending is not OK or OK_EMPTY, this function
    // shall invoke handleMessageException with that message.]
    @Test
    public void sendPacketReceivesStatusThatIsNotOkOrOkEmpty() throws TransportException
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Map<String, IotHubTransportPacket> inProgressMessages = new HashMap<>();
        Deencapsulation.setField(transport, "inProgressPackets", inProgressMessages);
        Deencapsulation.setField(transport, "iotHubTransportConnection", mockedHttpsIotHubConnection);
        new NonStrictExpectations(IotHubTransport.class)
        {
            {
                mockedPacket.getMessage();
                result = mockedTransportMessage;

                mockedTransportMessage.isMessageAckNeeded((IotHubClientProtocol) any);
                result = true;

                mockedHttpsIotHubConnection.sendMessage((Message) any);
                result = IotHubStatusCode.HUB_OR_DEVICE_ID_NOT_FOUND;

                Deencapsulation.invoke(transport, "handleMessageException", new Class[] {IotHubTransportPacket.class, TransportException.class}, mockedPacket, any);
            }
        };

        //act
        Deencapsulation.invoke(transport, "sendPacket", mockedPacket);

        //assert
        assertEquals(0, inProgressMessages.size());
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "handleMessageException", new Class[] {IotHubTransportPacket.class, TransportException.class}, mockedPacket, any);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_075: [If the response from sending is OK or OK_EMPTY and no ack is expected,
    // this function shall put that set that status in the sent packet and add that packet to the callbacks queue.]
    @Test
    public void sendPacketHappyPathWithoutAck() throws TransportException
    {
        //arrange
        IotHubTransport transport = new IotHubTransport(mockedConfig);
        Map<String, IotHubTransportPacket> inProgressMessages = new HashMap<>();
        Queue<IotHubTransportPacket> callbackPacketsQueue = new ConcurrentLinkedQueue<>();
        Deencapsulation.setField(transport, "callbackPacketsQueue", callbackPacketsQueue);
        Deencapsulation.setField(transport, "inProgressPackets", inProgressMessages);
        Deencapsulation.setField(transport, "iotHubTransportConnection", mockedHttpsIotHubConnection);
        new NonStrictExpectations()
        {
            {
                mockedPacket.getMessage();
                result = mockedTransportMessage;

                mockedTransportMessage.isMessageAckNeeded((IotHubClientProtocol) any);
                result = false;

                mockedHttpsIotHubConnection.sendMessage((Message) any);
                result = IotHubStatusCode.OK_EMPTY;
            }
        };

        //act
        Deencapsulation.invoke(transport, "sendPacket", mockedPacket);

        //assert
        assertEquals(0, inProgressMessages.size());
        assertEquals(1, callbackPacketsQueue.size());
        new Verifications()
        {
            {
                mockedPacket.setStatus(IotHubStatusCode.OK_EMPTY);
                times = 1;
            }
        };
    }


    //Tests_SRS_IOTHUBTRANSPORT_34_076: [If an exception is encountered while sending the message, this function
    // shall invoke handleMessageException with that packet.]
    @Test
    public void sendPacketFailsToSend() throws TransportException
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Map<String, IotHubTransportPacket> inProgressMessages = new HashMap<>();
        Queue<IotHubTransportPacket> callbackPacketsQueue = new ConcurrentLinkedQueue<>();
        Deencapsulation.setField(transport, "callbackPacketsQueue", callbackPacketsQueue);
        Deencapsulation.setField(transport, "inProgressPackets", inProgressMessages);
        Deencapsulation.setField(transport, "iotHubTransportConnection", mockedHttpsIotHubConnection);
        new NonStrictExpectations(IotHubTransport.class)
        {
            {
                mockedPacket.getMessage();
                result = mockedTransportMessage;

                mockedTransportMessage.isMessageAckNeeded((IotHubClientProtocol) any);
                result = false;

                mockedHttpsIotHubConnection.sendMessage((Message) any);
                result = mockedTransportException;

                Deencapsulation.invoke(transport, "handleMessageException", new Class[] {IotHubTransportPacket.class, TransportException.class}, mockedPacket, mockedTransportException);
            }
        };

        //act
        Deencapsulation.invoke(transport, "sendPacket", mockedPacket);

        //assert
        assertEquals(0, inProgressMessages.size());
        assertEquals(0, callbackPacketsQueue.size());
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "handleMessageException", new Class[] {IotHubTransportPacket.class, TransportException.class}, mockedPacket, mockedTransportException);
                times = 1;
            }
        };
    }

    //Tests_SRS_IOTHUBTRANSPORT_34_076: [If an exception is encountered while sending the message, this function
    // shall invoke handleMessageException with that packet.]
    @Test
    public void sendPacketFailsToSendAndExpectsAck() throws TransportException
    {
        //arrange
        final IotHubTransport transport = new IotHubTransport(mockedConfig);
        Map<String, IotHubTransportPacket> inProgressMessages = new HashMap<>();
        Queue<IotHubTransportPacket> callbackPacketsQueue = new ConcurrentLinkedQueue<>();
        Deencapsulation.setField(transport, "callbackPacketsQueue", callbackPacketsQueue);
        Deencapsulation.setField(transport, "inProgressPackets", inProgressMessages);
        Deencapsulation.setField(transport, "iotHubTransportConnection", mockedHttpsIotHubConnection);
        new NonStrictExpectations(IotHubTransport.class)
        {
            {
                mockedPacket.getMessage();
                result = mockedTransportMessage;

                mockedTransportMessage.isMessageAckNeeded((IotHubClientProtocol) any);
                result = true;

                mockedHttpsIotHubConnection.sendMessage((Message) any);
                result = mockedTransportException;

                Deencapsulation.invoke(transport, "handleMessageException", new Class[] {IotHubTransportPacket.class, TransportException.class}, mockedPacket, mockedTransportException);
            }
        };

        //act
        Deencapsulation.invoke(transport, "sendPacket", mockedPacket);

        //assert
        assertEquals(0, inProgressMessages.size());
        assertEquals(0, callbackPacketsQueue.size());
        new Verifications()
        {
            {
                Deencapsulation.invoke(transport, "handleMessageException", new Class[] {IotHubTransportPacket.class, TransportException.class}, mockedPacket, mockedTransportException);
                times = 1;
            }
        };
    }
}