/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.qpid.qpid_interop_test.jms_large_content_test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import org.apache.qpid.jms.JmsConnectionFactory;

public class Receiver {
    private static final String USER = "guest";
    private static final String PASSWORD = "guest";
    private static final int TIMEOUT = 1000;
    private static final String[] SUPPORTED_JMS_MESSAGE_TYPES = {"JMS_MESSAGE_TYPE",
                                                                 "JMS_BYTESMESSAGE_TYPE",
                                                                 "JMS_MAPMESSAGE_TYPE",
                                                                 "JMS_OBJECTMESSAGE_TYPE",
                                                                 "JMS_STREAMMESSAGE_TYPE",
                                                                 "JMS_TEXTMESSAGE_TYPE"};
    private static enum JMS_DESTINATION_TYPE {JMS_QUEUE, JMS_TEMPORARY_QUEUE, JMS_TOPIC, JMS_TEMPORARY_TOPIC};
    
    Connection _connection;
    Session _session;
    Queue _queue;
    MessageConsumer _messageConsumer;
    JsonObjectBuilder _jsonTestValueMapBuilder;
    
    // args[0]: Broker URL
    // args[1]: Queue name
    // args[2]: JMS message type
    // args[3]: JSON Test parameters containing testValuesMap
    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.out.println("JmsReceiverShim: Incorrect number of arguments");
            System.out.println("JmsReceiverShim: Expected arguments: broker_address, queue_name, JMS_msg_type, JSON_receive_params");
            System.exit(1);
        }
        String brokerAddress = "amqp://" + args[0];
        String queueName = args[1];
        String jmsMessageType = args[2];
        if (!isSupportedJmsMessageType(jmsMessageType)) {
            System.out.println("ERROR: JmsReceiverShim: Unknown or unsupported JMS message type \"" + jmsMessageType + "\"");
            System.exit(1);
        }

        JsonReader jsonReader = Json.createReader(new StringReader(args[3]));
        JsonObject numTestValuesMap = jsonReader.readObject();
        jsonReader.close();
        
        Receiver shim = new Receiver(brokerAddress, queueName);
        shim.run(jmsMessageType, numTestValuesMap);
    }

    public Receiver(String brokerAddress, String queueName) {
        try {
            _connection = null;
            ConnectionFactory factory = (ConnectionFactory)new JmsConnectionFactory(brokerAddress);
            _connection = factory.createConnection(USER, PASSWORD);
            _connection.setExceptionListener(new MyExceptionListener());
            _connection.start();

            _session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            _queue = _session.createQueue(queueName);

            _messageConsumer = _session.createConsumer(_queue);

            _jsonTestValueMapBuilder = Json.createObjectBuilder();
        } catch (Exception exc) {
            if (_connection != null)
                try { _connection.close(); } catch (JMSException e) {}
            System.out.println("Caught exception, exiting.");
            exc.printStackTrace(System.out);
            System.exit(1);
        }
    }
    
    public void run(String jmsMessageType, JsonObject numTestValuesMap) {
        try {
            List<String> subTypeKeyList = new ArrayList<String>(numTestValuesMap.keySet());
            Collections.sort(subTypeKeyList);
            
            Message message = null;
            
            for (String subType: subTypeKeyList) {
                JsonArrayBuilder jasonTestValuesArrayBuilder = Json.createArrayBuilder();
                for (int i=0; i<numTestValuesMap.getJsonNumber(subType).intValue(); ++i) {
                    message = _messageConsumer.receive(TIMEOUT);
                    if (message == null) break;
                    switch (jmsMessageType) {
                    case "JMS_MESSAGE_TYPE":
                        processJMSMessage(jasonTestValuesArrayBuilder);
                        break;
                    case "JMS_BYTESMESSAGE_TYPE":
                        processJMSBytesMessage(jmsMessageType, subType, message, jasonTestValuesArrayBuilder);
                        break;
                    case "JMS_STREAMMESSAGE_TYPE":
                        processJMSStreamMessage(jmsMessageType, subType, message, jasonTestValuesArrayBuilder);
                        break;
                    case "JMS_MAPMESSAGE_TYPE":
                        processJMSMapMessage(jmsMessageType, subType, i, message, jasonTestValuesArrayBuilder);
                        break;
                    case "JMS_OBJECTMESSAGE_TYPE":
                        processJMSObjectMessage(subType, message, jasonTestValuesArrayBuilder);
                        break;
                    case "JMS_TEXTMESSAGE_TYPE":
                        processJMSTextMessage(message, jasonTestValuesArrayBuilder);
                        break;
                    default:
                        _connection.close();
                        throw new Exception("JmsReceiverShim: Internal error: Unknown or unsupported JMS message type \"" + jmsMessageType + "\"");
                    }
                }
                _jsonTestValueMapBuilder.add(subType, jasonTestValuesArrayBuilder);
            }
            _connection.close();
    
            System.out.println(jmsMessageType);
            StringWriter out = new StringWriter();
            writeJsonObject(_jsonTestValueMapBuilder, out);
            System.out.println(out.toString());        
        } catch (Exception exp) {
            try { _connection.close(); } catch (JMSException e) {}
            System.out.println("Caught exception, exiting.");
            exp.printStackTrace(System.out);
            System.exit(1);
        }
    }
    
    protected void processJMSMessage(JsonArrayBuilder jasonTestValuesArrayBuilder) {
        jasonTestValuesArrayBuilder.addNull();        
    }
    
    protected void processJMSBytesMessage(String jmsMessageType, String subType, Message message, JsonArrayBuilder jasonTestValuesArrayBuilder) throws Exception, JMSException, IOException, ClassNotFoundException {
        switch (subType) {
        case "boolean":
            jasonTestValuesArrayBuilder.add(((BytesMessage)message).readBoolean()?"True":"False");
            break;
        case "byte":
            jasonTestValuesArrayBuilder.add(formatByte(((BytesMessage)message).readByte()));
            break;
        case "bytes":
            {
                byte[] bytesBuff = new byte[65536];
                int numBytesRead = ((BytesMessage)message).readBytes(bytesBuff);
                if (numBytesRead >= 0) {
                    jasonTestValuesArrayBuilder.add(new String(Arrays.copyOfRange(bytesBuff, 0, numBytesRead)));
                } else {
                    // NOTE: For this case, an empty byte array has nothing to return
                    jasonTestValuesArrayBuilder.add(new String());
                }
            }
            break;
        case "char":
            jasonTestValuesArrayBuilder.add(formatChar(((BytesMessage)message).readChar()));
            break;
        case "double":
            long l = Double.doubleToRawLongBits(((BytesMessage)message).readDouble());
            jasonTestValuesArrayBuilder.add(String.format("0x%16s", Long.toHexString(l)).replace(' ', '0'));
            break;
        case "float":
            int i0 = Float.floatToRawIntBits(((BytesMessage)message).readFloat());
            jasonTestValuesArrayBuilder.add(String.format("0x%8s", Integer.toHexString(i0)).replace(' ', '0'));
            break;
        case "int":
            jasonTestValuesArrayBuilder.add(formatInt(((BytesMessage)message).readInt()));
            break;
        case "long":
            jasonTestValuesArrayBuilder.add(formatLong(((BytesMessage)message).readLong()));
            break;
        case "object":
            {
                byte[] bytesBuff = new byte[65536];
                int numBytesRead = ((BytesMessage)message).readBytes(bytesBuff);
                if (numBytesRead >= 0) {
                    ByteArrayInputStream bais = new ByteArrayInputStream(Arrays.copyOfRange(bytesBuff, 0, numBytesRead));
                    ObjectInputStream ois = new ObjectInputStream(bais);
                    Object obj = ois.readObject();
                    jasonTestValuesArrayBuilder.add(obj.getClass().getName() + ":" + obj.toString());
                } else {
                    jasonTestValuesArrayBuilder.add("<object error>");
                }
            }
            break;
        case "short":
            jasonTestValuesArrayBuilder.add(formatShort(((BytesMessage)message).readShort()));
            break;
        case "string":
            jasonTestValuesArrayBuilder.add(((BytesMessage)message).readUTF());
            break;
        default:
            throw new Exception("JmsReceiverShim: Unknown subtype for " + jmsMessageType + ": \"" + subType + "\"");
        }        
    }
    
    protected void processJMSMapMessage(String jmsMessageType, String subType, int count, Message message, JsonArrayBuilder jasonTestValuesArrayBuilder) throws Exception, JMSException {
        String name = String.format("%s%03d", subType, count);
        switch (subType) {
        case "boolean":
            jasonTestValuesArrayBuilder.add(((MapMessage)message).getBoolean(name)?"True":"False");
            break;
        case "byte":
            jasonTestValuesArrayBuilder.add(formatByte(((MapMessage)message).getByte(name)));
            break;
        case "bytes":
            jasonTestValuesArrayBuilder.add(new String(((MapMessage)message).getBytes(name)));
            break;
        case "char":
            jasonTestValuesArrayBuilder.add(formatChar(((MapMessage)message).getChar(name)));
            break;
        case "double":
            long l = Double.doubleToRawLongBits(((MapMessage)message).getDouble(name));
            jasonTestValuesArrayBuilder.add(String.format("0x%16s", Long.toHexString(l)).replace(' ', '0'));
            break;
        case "float":
            int i0 = Float.floatToRawIntBits(((MapMessage)message).getFloat(name));
            jasonTestValuesArrayBuilder.add(String.format("0x%8s", Integer.toHexString(i0)).replace(' ', '0'));
            break;
        case "int":
            jasonTestValuesArrayBuilder.add(formatInt(((MapMessage)message).getInt(name)));
            break;
        case "long":
            jasonTestValuesArrayBuilder.add(formatLong(((MapMessage)message).getLong(name)));
            break;
        case "object":
            Object obj = ((MapMessage)message).getObject(name);
            jasonTestValuesArrayBuilder.add(obj.getClass().getName() + ":" + obj.toString());
            break;
        case "short":
            jasonTestValuesArrayBuilder.add(formatShort(((MapMessage)message).getShort(name)));
            break;
        case "string":
            jasonTestValuesArrayBuilder.add(((MapMessage)message).getString(name));
            break;
        default:
            throw new Exception("JmsReceiverShim: Unknown subtype for " + jmsMessageType + ": \"" + subType + "\"");
        }        
    }
    
    protected void processJMSObjectMessage(String subType, Message message, JsonArrayBuilder jasonTestValuesArrayBuilder) throws JMSException {
        jasonTestValuesArrayBuilder.add(((ObjectMessage)message).getObject().toString());
    }
    
    protected void processJMSStreamMessage(String jmsMessageType, String subType, Message message, JsonArrayBuilder jasonTestValuesArrayBuilder) throws Exception, JMSException {
        switch (subType) {
        case "boolean":
            jasonTestValuesArrayBuilder.add(((StreamMessage)message).readBoolean()?"True":"False");
            break;
        case "byte":
            jasonTestValuesArrayBuilder.add(formatByte(((StreamMessage)message).readByte()));
            break;
        case "bytes":
            byte[] bytesBuff = new byte[65536];
            int numBytesRead = ((StreamMessage)message).readBytes(bytesBuff);
            if (numBytesRead >= 0) {
                jasonTestValuesArrayBuilder.add(new String(Arrays.copyOfRange(bytesBuff, 0, numBytesRead)));
            } else {
                System.out.println("StreamMessage.readBytes() returned " + numBytesRead);
                jasonTestValuesArrayBuilder.add("<bytes error>");
            }
            break;
        case "char":
            jasonTestValuesArrayBuilder.add(formatChar(((StreamMessage)message).readChar()));
            break;
        case "double":
            long l = Double.doubleToRawLongBits(((StreamMessage)message).readDouble());
            jasonTestValuesArrayBuilder.add(String.format("0x%16s", Long.toHexString(l)).replace(' ', '0'));
            break;
        case "float":
            int i0 = Float.floatToRawIntBits(((StreamMessage)message).readFloat());
            jasonTestValuesArrayBuilder.add(String.format("0x%8s", Integer.toHexString(i0)).replace(' ', '0'));
            break;
        case "int":
            jasonTestValuesArrayBuilder.add(formatInt(((StreamMessage)message).readInt()));
            break;
        case "long":
            jasonTestValuesArrayBuilder.add(formatLong(((StreamMessage)message).readLong()));
            break;
        case "object":
            Object obj = ((StreamMessage)message).readObject();
            jasonTestValuesArrayBuilder.add(obj.getClass().getName() + ":" + obj.toString());
            break;
        case "short":
            jasonTestValuesArrayBuilder.add(formatShort(((StreamMessage)message).readShort()));
            break;
        case "string":
            jasonTestValuesArrayBuilder.add(((StreamMessage)message).readString());
            break;
        default:
            throw new Exception("JmsReceiverShim: Unknown subtype for " + jmsMessageType + ": \"" + subType + "\"");
        }        
    }

    protected void processJMSTextMessage(Message message, JsonArrayBuilder jasonTestValuesArrayBuilder) throws JMSException {
        jasonTestValuesArrayBuilder.add(((TextMessage)message).getText());
    }

    protected static void writeJsonObject(JsonObjectBuilder builder, StringWriter out) {
        JsonWriter jsonWriter = Json.createWriter(out);
        jsonWriter.writeObject(builder.build());
        jsonWriter.close();        
    }

    protected static String formatByte(byte b) {
        boolean neg = false;
        if (b < 0) {
            neg = true;
            b = (byte)-b;
        }
        return String.format("%s0x%x", neg?"-":"", b);
    }
    
    protected static String formatChar(char c) {
        if (Character.isLetterOrDigit(c)) {
            return String.format("%c", c);
        }
        char[] ca = {c};
        return new String(ca);
    }
    
    protected static String formatInt(int i) {
        boolean neg = false;
        if (i < 0) {
            neg = true;
            i = -i;
        }
        return String.format("%s0x%x", neg?"-":"", i);
    }
    
    protected static String formatLong(long l) {
        boolean neg = false;
        if (l < 0) {
            neg = true;
            l = -l;
        }
        return String.format("%s0x%x", neg?"-":"", l);
    }
    
    protected static String formatShort(int s) {
        boolean neg = false;
        if (s < 0) {
            neg = true;
            s = -s;
        }
        return String.format("%s0x%x", neg?"-":"", s);
    }
        
    protected static boolean isSupportedJmsMessageType(String jmsMessageType) {
        for (String supportedJmsMessageType: SUPPORTED_JMS_MESSAGE_TYPES) {
            if (jmsMessageType.equals(supportedJmsMessageType))
                return true;
        }
    return false;
    }

    private static class MyExceptionListener implements ExceptionListener {
        @Override
        public void onException(JMSException exception) {
            System.out.println("Connection ExceptionListener fired, exiting.");
            exception.printStackTrace(System.out);
            System.exit(1);
        }
    }
}