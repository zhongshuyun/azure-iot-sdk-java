/*
 *  Copyright (c) Microsoft. All rights reserved.
 *  Licensed under the MIT license. See LICENSE file in the project root for full license information.
 */

package com.microsoft.azure.sdk.iot.device;

import com.microsoft.azure.sdk.iot.device.DeviceTwin.DeviceMethod;
import com.microsoft.azure.sdk.iot.device.DeviceTwin.DeviceTwin;
import com.microsoft.azure.sdk.iot.device.exceptions.ModuleClientException;
import com.microsoft.azure.sdk.iot.device.fileupload.FileUpload;
import com.microsoft.azure.sdk.iot.device.transport.amqps.IoTHubConnectionType;
import org.bouncycastle.math.raw.Mod;

import java.io.IOException;
import java.net.URISyntaxException;

import static com.microsoft.azure.sdk.iot.device.DeviceClient.*;

public class ModuleClient
{
    private DeviceClientConfig config;
    private DeviceIO deviceIO;

    private DeviceTwin deviceTwin;
    private DeviceMethod deviceMethod;
    private FileUpload fileUpload;

    protected long RECEIVE_PERIOD_MILLIS;


    public ModuleClient(String connectionString, IotHubClientProtocol protocol) throws ModuleClientException
    {
        commonConstructorVerification(connectionString, protocol);

        if (protocol != IotHubClientProtocol.MQTT)
        {
            throw new UnsupportedOperationException();
        }

        try
        {
            IotHubConnectionString iotHubConnectionString = new IotHubConnectionString(connectionString);

            if (iotHubConnectionString.getModuleId() == null || iotHubConnectionString.getModuleId().isEmpty())
            {
                throw new UnsupportedOperationException("Connection string must contain field for ModuleId");
            }

            this.config = new DeviceClientConfig(iotHubConnectionString, DeviceClientConfig.AuthType.SAS_TOKEN);
            this.config.setProtocol(protocol);
        }
        catch (URISyntaxException e)
        {
            throw new ModuleClientException();
        }


        //Codes_SRS_DEVICECLIENT_21_004: [If the connection string is null or empty, the function shall throw an IllegalArgumentException.]
        //Codes_SRS_DEVICECLIENT_21_005: [If protocol is null, the function shall throw an IllegalArgumentException.]

        //Codes_SRS_DEVICECLIENT_21_002: [The constructor shall initialize the IoT Hub transport for the protocol specified, creating a instance of the deviceIO.]
        //Codes_SRS_DEVICECLIENT_21_003: [The constructor shall save the connection configuration using the object DeviceClientConfig.]
        //Codes_SRS_DEVICECLIENT_12_012: [The constructor shall set the connection type to SINGLE_CLIENT.]
        commonConstructorSetup(protocol);

    }

    public void open() throws ModuleClientException
    {
        try
        {
            this.deviceIO.open();
        }
        catch (IOException e)
        {
            throw new ModuleClientException(e);
        }
    }

    public void closeNow() throws ModuleClientException
    {
        try
        {
            this.deviceIO.close();

            if (this.fileUpload != null)
            {
                this.fileUpload.closeNow();
            }
        }
        catch (IOException e)
        {
            throw new ModuleClientException(e);
        }
    }

    // Default send to IoT/Edge Hub
    public void sendEventAsync(Message message, IotHubEventCallback callback, Object callbackContext)
    {
        message.setUserId(this.config.getDeviceId() + "/" + this.config.getModuleId());

        deviceIO.sendEventAsync(message, callback, callbackContext, this.config.getIotHubConnectionString());
    }

    // Overload for sending to specific output
    public void sendEventAsync(String outputName, Message message, IotHubEventCallback callback, Object callbackContext)
    {
        message.setOutputName(outputName);
        message.setUserId(this.config.getDeviceId() + "/" + this.config.getModuleId());

        //TODO outputname is what now?
        deviceIO.sendEventAsync(message, callback, callbackContext, this.config.getIotHubConnectionString());
    }


    // Twin
    //public Task<Twin> GetTwinAsync() {}





    /**
     * Throws an IllegalArgumentException if either of the arguments is null or if the connString is empty.
     * @param connString the connection string
     * @param protocol the transport protocol
     */
    private void commonConstructorVerification(String connString, IotHubClientProtocol protocol)
    {
        if (connString == null || connString.isEmpty())
        {
            throw new IllegalArgumentException("Connection string cannot be null or empty");
        }

        if (protocol == null)
        {
            throw new IllegalArgumentException("Protocol cannot be null.");
        }
    }

    /**
     * Handles logic common to all constructors of DeviceClient. Sets the receive period based on the protocol, creates the config instance,
     * creates the deviceIO instance, and creates the logger instance
     * @param protocol The protocol the device client will communicate in
     * @throws IllegalArgumentException if the connection string is null, empty, or invalid, if the protocol is null, if the connection string is for x509 when x509 is not allowed
     * @throws SecurityException if the provided connection string contains an expired sas token
     * @throws URISyntaxException if the hostname in the connection string is not a valid URI
     */
    private void commonConstructorSetup(IotHubClientProtocol protocol)
    {
        switch (protocol)
        {
            case HTTPS:
                RECEIVE_PERIOD_MILLIS = RECEIVE_PERIOD_MILLIS_HTTPS;
                break;
            case AMQPS:
                RECEIVE_PERIOD_MILLIS = RECEIVE_PERIOD_MILLIS_AMQPS;
                break;
            case AMQPS_WS:
                RECEIVE_PERIOD_MILLIS = RECEIVE_PERIOD_MILLIS_AMQPS;
                break;
            case MQTT:
                RECEIVE_PERIOD_MILLIS = RECEIVE_PERIOD_MILLIS_MQTT;
                break;
            case MQTT_WS:
                RECEIVE_PERIOD_MILLIS = RECEIVE_PERIOD_MILLIS_MQTT;
                break;
            default:
                // should never happen.
                throw new IllegalStateException(
                        "Invalid client protocol specified.");
        }

        this.deviceIO = new DeviceIO(this.config, SEND_PERIOD_MILLIS, RECEIVE_PERIOD_MILLIS);
    }
}
