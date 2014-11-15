package com.pusher.client.channel.impl;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.pusher.client.AuthorizationFailureException;
import com.pusher.client.Authorizer;
import com.pusher.client.channel.ChannelState;
import com.pusher.client.channel.PrivateChannel;
import com.pusher.client.channel.PrivateChannelEventListener;
import com.pusher.client.channel.SubscriptionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.impl.InternalConnection;
import com.pusher.client.util.Factory;

import java.util.LinkedHashMap;
import java.util.Map;

public class PrivateChannelImpl extends ChannelImpl implements PrivateChannel {

    private static final String CLIENT_EVENT_PREFIX = "client-";
    private final InternalConnection connection;
    private Authorizer authorizer;

    public PrivateChannelImpl(InternalConnection connection, String channelName,
            Authorizer authorizer, Factory factory) {
        super(channelName, factory);
        this.connection = connection;
        this.authorizer = authorizer;
    }

    /* PrivateChannel implementation */

    @Override
    @SuppressWarnings("rawtypes")
    public void trigger(String eventName, String data) {

        if (eventName == null || !eventName.startsWith(CLIENT_EVENT_PREFIX)) {
            throw new IllegalArgumentException("Cannot trigger event " + eventName
                    + ": client events must start with \"client-\"");
        }

        if (this.state != ChannelState.SUBSCRIBED) {
            throw new IllegalStateException("Cannot trigger event " + eventName
                    + " because channel " + name + " is in " + state.toString()
                    + " state");
        }

        if (connection.getState() != ConnectionState.CONNECTED) {
            throw new IllegalStateException("Cannot trigger event " + eventName
                    + " because connection is in " + connection.getState().toString()
                    + " state");
        }

        try {
            Map userData = new Gson().fromJson(data, Map.class);

            Map<Object, Object> jsonPayload = new LinkedHashMap<Object, Object>();
            jsonPayload.put("event", eventName);
            jsonPayload.put("channel", name);
            jsonPayload.put("data", userData);

            String jsonMessage = new Gson().toJson(jsonPayload);
            connection.sendMessage(jsonMessage);

        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Cannot trigger event " + eventName
                    + " because \"" + data + "\" could not be parsed as valid JSON");
        }
    }

    /* Base class overrides */

    @Override
    public void bind(String eventName, SubscriptionEventListener listener) {

        if ((listener instanceof PrivateChannelEventListener) == false) {
            throw new IllegalArgumentException(
                    "Only instances of PrivateChannelEventListener can be bound to a private channel");
        }

        super.bind(eventName, listener);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public String toSubscribeMessage() {

        String authResponse = getAuthResponse();

        try {
            Map authResponseMap = new Gson().fromJson(authResponse, Map.class);
            String authKey = (String) authResponseMap.get("auth");

            Map<Object, Object> jsonObject = new LinkedHashMap<Object, Object>();
            jsonObject.put("event", "pusher:subscribe");

            Map<Object, Object> dataMap = new LinkedHashMap<Object, Object>();
            dataMap.put("channel", name);
            dataMap.put("auth", authKey);

            jsonObject.put("data", dataMap);

            String json = new Gson().toJson(jsonObject);
            return json;
        } catch (Exception e) {
            throw new AuthorizationFailureException(
                    "Unable to parse response from Authorizer: " + authResponse, e);
        }
    }

    @Override
    protected String[] getDisallowedNameExpressions() {
        return new String[] { "^(?!private-).*" };
    }

    /**
     * Protected access because this is also used by PresenceChannelImpl.
     */
    protected String getAuthResponse() {
        String socketId = connection.getSocketId();
        return authorizer.authorize(this.getName(), socketId);
    }

    @Override
    public String toString() {
        return String.format("[Private Channel: name=%s]", name);
    }
}
