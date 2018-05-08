// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for full license information.

package com.microsoft.azure.sdk.iot.device;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

public class Message
{
    // ----- Constants -----

    public static final Charset DEFAULT_IOTHUB_MESSAGE_CHARSET = StandardCharsets.UTF_8;


    // ----- Data Fields -----

    /**
     * [Required for two way requests] Used to correlate two-way communication.
     * Format: A case-sensitive string (up to 128 char long) of ASCII 7-bit alphanumeric chars
     * plus {'-', ':', '/', '\', '.', '+', '%', '_', '#', '*', '?', '!', '(', ')', ',', '=', '@', ';', '$', '''}.
     * Non-alphanumeric characters are from URN RFC.
     */
    private String messageId;

    /// <summary>
    /// Destination of the message
    /// </summary>
    private String to;

    /// <summary>
    /// [Optional] Expiry time in milliseconds.
    /// </summary>
    private long expiryTime;

    /// <summary>
    /// Used by receiver to Abandon, Reject or Complete the message
    /// </summary>
    private String lockToken;

    /// <summary>
    /// Used in message responses and feedback
    /// </summary>
    private String correlationId;

    /// <summary>
    /// [Required in feedback messages] Used to specify the entity creating the message.
    /// </summary>
    private String userId;

    /// <summary>
    /// [Optional] On C2D messages it is interpreted by hub to specify the expected feedback messages. Ignored in other cases.
    /// </summary>
    private FeedbackStatusCodeEnum ack;

    /// <summary>
    /// [Optional] Used when batching on HTTP Default: false.
    /// </summary>
    private Boolean httpBatchSerializeAsString;

    /// <summary>
    /// [Optional] Used when batching on HTTP Default: UTF-8.
    /// </summary>
    private StandardCharsets httpBatchEncoding;

    /// <summary>
    /// [Stamped on servicebound messages by IoT Hub] The authenticated id used to send this message.
    /// </summary>
    private String connectionDeviceId;

    /// <summary>
    /// [Stamped on servicebound messages by IoT Hub] The generationId of the authenticated device used to send this message.
    /// </summary>
    private String connectionDeviceGenerationId;

    /// <summary>
    /// [Stamped on servicebound messages by IoT Hub] The authentication type used to send this message, format as in IoT Hub Specs
    /// </summary>
    private String connectionAuthenticationMethod;

    /// <summary>
    /// [Required in feedback messages] Used in feedback messages generated by IoT Hub.
    /// 0 = success 1 = message expired 2 = max delivery count exceeded 3 = message rejected
    /// </summary>
    private FeedbackStatusCodeEnum feedbackStatusCode;

    /// <summary>
    /// [Required in feedback messages] Used in feedback messages generated by IoT Hub. "success", "Message expired", "Max delivery count exceeded", "Message rejected"
    /// </summary>
    private String feedbackDescription;

    /// <summary>
    /// [Required in feedback messages] Used in feedback messages generated by IoT Hub.
    /// </summary>
    private String feedbackDeviceId;

    /// <summary>
    /// [Required in feedback messages] Used in feedback messages generated by IoT Hub.
    /// </summary>
    private String feedbackDeviceGenerationId;

    /// <summary>
    /// [Optional] Used to specify the type of message exchanged between Iot Hub and Device
    /// </summary>
    private MessageType messageType;

    /// <summary>
    /// [Optional] Used to specify the sender device client for multiplexing scenarios
    /// </summary>
    private IotHubConnectionString iotHubConnectionString;

    private String connectionModuleId;
    private String inputName;
    private String outputName;

    private String deliveryAcknowledgement;

    /**
     * User-defined properties.
     */
    private ArrayList<MessageProperty> properties;

    /// <summary>
    /// The message body
    /// </summary>
    private byte[] body;

    /**
     * Stream that will provide the bytes for the body of the
     */
    private ByteArrayInputStream bodyStream;
    private CustomLogger logger;

    // ----- Constructors -----

    /**
     * Constructor.
     */
    public Message()
    {
        initialize();
    }

    /**
     * Constructor.
     * @param stream A stream to provide the body of the new Message instance.
     */
    public Message(ByteArrayInputStream stream)
    {
        initialize();
    }

    /**
     * Constructor.
     * @param body The body of the new Message instance.
     */
    public Message(byte[] body)
    {
        // Codes_SRS_MESSAGE_11_025: [If the message body is null, the constructor shall throw an IllegalArgumentException.]
        if (body == null)
        {
            throw new IllegalArgumentException("Message body cannot be 'null'.");
        }

        initialize();

        // Codes_SRS_MESSAGE_11_024: [The constructor shall save the message body.]
        this.body = body;
    }

    /**
     * Constructor.
     * @param body The body of the new Message instance. It is internally serialized to a byte array using UTF-8 encoding.
     */
    public Message(String body)
    {
        if (body == null)
        {
            throw new IllegalArgumentException("Message body cannot be 'null'.");
        }

        initialize();

        this.body = body.getBytes(DEFAULT_IOTHUB_MESSAGE_CHARSET);
    }

    
    // ----- Public Methods -----

    /// <summary>
    /// The stream content of the body.
    /// </summary>
    public ByteArrayOutputStream getBodyStream()
    {
        return null;
    }

    /**
     * The byte content of the body.
     * @return A copy of this Message body, as a byte array.
     */
    public byte[] getBytes()
    {
        // Codes_SRS_MESSAGE_11_002: [The function shall return the message body.]
        byte[] bodyClone = null;

        if (this.body != null) {
            bodyClone = Arrays.copyOf(this.body, this.body.length);
        }

        return bodyClone;
    }

    /**
     * Gets the values of user-defined properties of this Message.
     * @param name Name of the user-defined property to search for.
     * @return The value of the property if it is set, or null otherwise.
     */
    public String getProperty(String name)
    {
        MessageProperty messageProperty = null;

        for (MessageProperty currentMessageProperty: this.properties)
        {
            if (currentMessageProperty.hasSameName(name))
            {
                messageProperty = currentMessageProperty;
                break;
            }
        }

        // Codes_SRS_MESSAGE_11_034: [If no value associated with the property name is found, the function shall return null.]
        if (messageProperty == null) {
            return null;
        }

        // Codes_SRS_MESSAGE_11_032: [The function shall return the value associated with the message property name, where the name can be either the HTTPS or AMQPS property name.]
        return messageProperty.getValue();
    }

    /**
     * Adds or sets user-defined properties of this Message.
     * @param name Name of the property to be set.
     * @param value Value of the property to be set.
     * @exception IllegalArgumentException If any of the arguments provided is null.
     */
    public void setProperty(String name, String value)
    {
        // Codes_SRS_MESSAGE_11_028: [If name is null, the function shall throw an IllegalArgumentException.]
        if (name == null)
        {
            throw new IllegalArgumentException("Property name cannot be 'null'.");
        }

        // Codes_SRS_MESSAGE_11_029: [If value is null, the function shall throw an IllegalArgumentException.]
        if (value == null)
        {
            throw new IllegalArgumentException("Property value cannot be 'null'.");
        }

        // Codes_SRS_MESSAGE_11_026: [The function shall set the message property to the given value.]
        MessageProperty messageProperty = null;

        for (MessageProperty currentMessageProperty: this.properties)
        {
            if (currentMessageProperty.hasSameName(name))
            {
                messageProperty = currentMessageProperty;
                break;
            }
        }

        if (messageProperty != null)
        {
            this.properties.remove(messageProperty);
        }

        logger.LogInfo("Setting message property with name=%s and value=%s, method name is %s ", name, value, logger.getMethodName());
        this.properties.add(new MessageProperty(name, value));
    }

    /**
     * Returns a copy of the message properties.
     *
     * @return a copy of the message properties.
     */
    public MessageProperty[] getProperties() {
        // Codes_SRS_MESSAGE_11_033: [The function shall return a copy of the message properties.]
        return properties.toArray(new MessageProperty[this.properties.size()]);
    }

    // ----- Private Methods -----

    /**
     * Internal initializer method for a new Message instance.
     */
    private void initialize() {
        this.lockToken = UUID.randomUUID().toString();
        this.messageId = UUID.randomUUID().toString();
        this.correlationId = UUID.randomUUID().toString();
        this.feedbackStatusCode = FeedbackStatusCodeEnum.none;
        this.ack = FeedbackStatusCodeEnum.none;
        this.properties = new ArrayList<>();
        this.logger = new CustomLogger(this.getClass());
    }

    /**
     * Verifies whether the message is expired or not
     * @return true if the message is expired, false otherwise
     */
    public boolean isExpired()
    {
        boolean messageExpired;

        // Codes_SRS_MESSAGE_15_035: [The function shall return false if the expiryTime is set to 0.]
        if (this.expiryTime == 0)
        {
            messageExpired = false;
        }
        else
        {
            // Codes_SRS_MESSAGE_15_036: [The function shall return true if the current time is greater than the expiry time and false otherwise.]
            long currentTime = System.currentTimeMillis();
            if (currentTime > expiryTime)
            {
                logger.LogWarn("The message with messageid %s expired on %s, method name is %s ", this.getMessageId(), new Date(), logger.getMethodName());
                messageExpired = true;
            }
            else
            {
                messageExpired = false;
            }
        }

        return messageExpired;
    }

    /**
     * Getter for the messageId property
     * @return The property value
     */
    public String getMessageId()
    {
        // Codes_SRS_MESSAGE_34_043: [The function shall return the message's message Id.]
        return messageId;
    }

    /**
     * Setter for the messageId property
     * @param messageId The string containing the property value
     */
    public void setMessageId(String messageId)
    {
        // Codes_SRS_MESSAGE_34_044: [The function shall set the message's message ID to the provided value.]
        this.messageId = messageId;
    }

    public void setUserId(String userId)
    {
        this.userId = userId;
    }

    /**
     * Getter for the correlationId property
     * @return The property value
     */
    public String getCorrelationId()
    {
        // Codes_SRS_MESSAGE_34_045: [The function shall return the message's correlation ID.]
        return correlationId;
    }

    /**
     * Setter for the correlationId property
     * @param correlationId The string containing the property value
     */
    public void setCorrelationId(String correlationId)
    {
        // Codes_SRS_MESSAGE_34_046: [The function shall set the message's correlation ID to the provided value.]
        this.correlationId = correlationId;
    }

    /**
     * Setter for the expiryTime property. This setter uses relative time, not absolute time.
     * @param timeOut The time out for the message, in milliseconds, from the current time.
     */
    public void setExpiryTime(long timeOut)
    {
        // Codes_SRS_MESSAGE_34_047: [The function shall set the message's expiry time.]
        long currentTime = System.currentTimeMillis();
        this.expiryTime = currentTime + timeOut;
        logger.LogInfo("The message with messageid %s has expiry time as %s milliseconds and the message will expire on %s, method name is %s ", this.getMessageId(), timeOut, new Date(this.expiryTime), logger.getMethodName());
    }

    /**
     * Setter for the expiryTime property using absolute time
     * @param absoluteTimeout The time out for the message, in milliseconds.
     */
    public void setAbsoluteExpiryTime(long absoluteTimeout)
    {
        // Codes_SRS_MESSAGE_34_038: [If the provided absolute expiry time is negative, an IllegalArgumentException shall be thrown.]
        if (absoluteTimeout < 0)
        {
            throw new IllegalArgumentException("ExpiryTime may not be negative");
        }

        // Codes_SRS_MESSAGE_34_037: [The function shall set the message's expiry time to be the number of milliseconds since the epoch provided in absoluteTimeout.]
        this.expiryTime = absoluteTimeout;
        logger.LogInfo("The message with messageid %s has expiry time as %s milliseconds and the message will expire on %s, method name is %s ", this.getMessageId(), absoluteTimeout, new Date(this.expiryTime), logger.getMethodName());
    }

    /**
     * Getter for the Message type
     * @return the Message type value
     */
    public MessageType getMessageType()
    {
        // Codes_SRS_MESSAGE_34_049: [The function shall return the message's message type.]
        return this.messageType;
    }

    public void setConnectionDeviceId(String connectionDeviceId)
    {
        this.connectionDeviceId = connectionDeviceId;
    }

    public void setConnectionModuleId(String connectionModuleId)
    {
        this.connectionModuleId = connectionModuleId;
    }

    public void setInputName(String inputName)
    {
        this.inputName = inputName;
    }

    public void setOutputName(String outputName)
    {
        this.outputName = outputName;
    }

    /**
     * Setter for the Message type
     * @param type The enum containing the Message type value
     */
    public void setMessageType(MessageType type)
    {
        // Codes_SRS_MESSAGE_34_048: [The function shall set the message's message type.]
        this.messageType = type;
    }

    /**
     * Getter for the To system property
     * @return the To value
     */
    public String getTo()
    {
        // Codes_SRS_MESSAGE_34_041: [The function shall return the message's To value.]
        return this.to;
    }

    public String getConnectionDeviceId()
    {
        return connectionDeviceId;
    }

    public String getConnectionModuleId()
    {
        return connectionModuleId;
    }

    public String getInputName()
    {
        return inputName;
    }

    public String getOutputName()
    {
        return outputName;
    }

    /**
     * Getter for the delivery acknowledgement system property
     * @return the delivery acknowledgement value
     */
    public String getDeliveryAcknowledgement()
    {
        // Codes_SRS_MESSAGE_34_039: [The function shall return the message's DeliveryAcknowledgement.]
        return this.deliveryAcknowledgement;
    }

    /**
     * Getter for the User ID system property
     * @return the User ID value
     */
    public String getUserId ()
    {
        // Codes_SRS_MESSAGE_34_037: [The function shall return the message's user ID.]
        return this.userId;
    }

    /**
     * Getter for the iotHubConnectionString property
     * @return the iotHubConnectionString value
     */
    public IotHubConnectionString getIotHubConnectionString()
    {
        // Codes_SRS_MESSAGE_12_001: [The function shall return the message's iotHubConnectionString object.]
        return iotHubConnectionString;
    }

    /**
     * Setter for the iotHubConnectionString type
     * @param iotHubConnectionString The iotHubConnectionString value to set
     */
    public void setIotHubConnectionString(IotHubConnectionString iotHubConnectionString)
    {
        // Codes_SRS_MESSAGE_12_002: [The function shall set the message's iotHubConnectionString object to the provided value.]
        this.iotHubConnectionString = iotHubConnectionString;
    }

}